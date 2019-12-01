import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import schema.ImplicitConversions._
import schema.service.{GetRequest, PostRequest, RequestService, RequestServiceClient}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

private object ChordialClient extends App {

  def pause(msg: String): Unit = {
    println(s"Press enter to continue - ${msg}")
    scala.io.StdIn readLine()
  }

  val config = ConfigFactory.load()

  val log = LoggerFactory.getLogger(ChordialClient.getClass)
  log.info("Client config loaded")

  implicit val actorSystem: ActorSystem = ActorSystem("HelloWorldClient", config)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  log.info("Implicit Akka structures initialized")

  val clientSettings = GrpcClientSettings.fromConfig(RequestService.name)
  val client = RequestServiceClient(clientSettings)
  log.info("Client instance initialized")

  
  pause("Send write request")

  log.info("Sending POST request")
  client
    .post(PostRequest(
      "abcdefg",
      "Hello there"))
    .onComplete {
      case Success(msg) => log.info(s"POST request successful: ${msg}")
      case Failure(e) => log.info(s"POST request failed: ${e}")
    }

  pause("Send read request")

  log.info("Sending GET request")
  client
    .get(GetRequest(
      "abcdefg"))
    .onComplete {
      case Success(msg) => {
        val stringValue: String = msg.value
        log.info(s"GET request successful: ${stringValue}")
      }
      case Failure(e) => log.info(s"GET request failed: ${e}")
    }

  pause("Closing program")
}
