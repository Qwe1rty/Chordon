package service

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import org.slf4j.LoggerFactory
import schema.RequestTrait
import schema.service._

import scala.concurrent.Future
import scala.concurrent.duration.Duration


object RequestServiceImpl {

  implicit final val DEFAULT_TIMEOUT: Timeout = Timeout(Duration(5, TimeUnit.SECONDS))
}


class RequestServiceImpl
  (requestServiceActor: ActorRef)(implicit mat: Materializer, timeout: Timeout) extends RequestService {

  final private val log = LoggerFactory.getLogger(RequestServiceImpl.getClass)


  override def get(in: GetRequest): Future[GetResponse] = {
    log.debug(s"Get request received with key ${in.key}")
    (requestServiceActor ? (GetRequest.asInstanceOf[RequestTrait])).mapTo[GetResponse]
  }

  override def post(in: PostRequest): Future[PostResponse] = {
    log.debug(s"Post request received with key ${in.key}")
    (requestServiceActor ? (PostRequest.asInstanceOf[RequestTrait])).mapTo[PostResponse]
  }

  override def delete(in: DeleteRequest): Future[DeleteResponse] = {
    log.debug(s"Delete request received with key ${in.key}")
    (requestServiceActor ? (DeleteRequest.asInstanceOf[RequestTrait])).mapTo[DeleteResponse]
  }
}
