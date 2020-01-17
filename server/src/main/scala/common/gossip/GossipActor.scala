package common.gossip

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.risksense.ipaddr.IpAddress
import common.ChordialDefaults
import common.gossip.GossipSignal.{ClusterSizeReceived, SendRPC}
import common.membership.types.NodeState
import common.utils.ActorTimers.Tick
import common.utils.{ActorDefaults, ActorTimers, GrpcSettingsFactory}
import membership.{Membership, MembershipAPI}
import schema.ImplicitDataConversions._

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success}


object GossipActor extends GrpcSettingsFactory {

  private case class PayloadTracker(payload: GossipPayload, var count: Int, cooldown: Int) {

    def apply(grpcClientSettings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext): Unit = {
      payload.rpc(grpcClientSettings)
      count -= 1
    }
  }


  def apply[KeyType: ClassTag]
      (membershipActor: ActorRef, delay: FiniteDuration, affiliation: String)
      (implicit actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(
      Props(new GossipActor[KeyType](membershipActor, delay)),
      s"gossipActor-${affiliation}"
    )
  }

  override def createGrpcSettings
      (ipAddress: IpAddress, timeout: FiniteDuration)
      (implicit actorSystem: ActorSystem): GrpcClientSettings = {

    GrpcClientSettings
      .connectToServiceAt(
        ipAddress,
        common.ChordialDefaults.MEMBERSHIP_PORT
      )
      .withDeadline(timeout)
  }
}


class GossipActor[KeyType: ClassTag] private
    (membershipActor: ActorRef, delay: FiniteDuration)
    (implicit actorSystem: ActorSystem)
  extends Actor
  with ActorLogging
  with ActorDefaults
  with ActorTimers {

  import GossipActor._

  implicit private val membershipAskTimeout: Timeout = delay // Semi-synchronous, can be bounded by cycle length
  implicit private val materializer: ActorMaterializer = ActorMaterializer()(context)
  implicit private val executionContext: ExecutionContext = actorSystem.dispatcher

  private val keyTable = mutable.Map[GossipKey[KeyType], PayloadTracker]()

  start(delay)


  override def receive: Receive = {

    case Tick => keyTable.foreach { gossipEntry =>

      (membershipActor ? MembershipAPI.GetRandomNode(NodeState.ALIVE))
        .mapTo[Option[Membership]]
        .onComplete(randomMemberRequest => self ! SendRPC(gossipEntry._1, randomMemberRequest))
    }

    case SendRPC(key: GossipKey[KeyType], randomMemberRequest) => randomMemberRequest match {

      case Success(requestResult) => requestResult.foreach(member => {
        val payload = keyTable(key)

        payload.count -= 1
        payload(createGrpcSettings(member.ipAddress, delay * 2))

        if (payload.count <= payload.cooldown) keyTable -= key
      })

      case Failure(e) => log.error(s"Error encountered on membership node request: ${e}")
    }


    case GossipAPI.PublishRequest(key: GossipKey[KeyType], payload) => {

      (membershipActor ? MembershipAPI.GetClusterSize)
        .mapTo[Int]
        .onComplete(self ! ClusterSizeReceived(key, payload, _))
    }

    case ClusterSizeReceived(key: GossipKey[KeyType], payload, clusterSizeRequest) => clusterSizeRequest match {

      case Success(clusterSize) => if (!keyTable.contains(key)) {
        val bufferCapacity = ChordialDefaults.bufferCapacity(clusterSize)
        keyTable += key -> PayloadTracker(payload, bufferCapacity, -3 * bufferCapacity)
      }

      case Failure(e) => log.error(s"Cluster size request could not be completed: ${e}")
    }


    case x => log.error(receivedUnknown(x))
  }
}