package server.service

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.IOResult

import scala.concurrent.Promise
import scala.reflect.ClassTag

object RequestActor {

  def props[A <: ResponseTrait]
      (requestPromise: Promise[A], ioProcessCallback: IOResult => A, operationRequest: OperationPackage)
      (implicit requestProcessorActor: ActorRef, ct: ClassTag[A]): Props =
    Props(new RequestActor[A](requestPromise, ioProcessCallback, operationRequest))
}

class RequestActor[+A <: ResponseTrait]
    (requestPromise: Promise[A], ioProcessCallback: IOResult => A, operationRequest: OperationPackage)
    (implicit requestProcessorActor: ActorRef, ct: ClassTag[A])
  extends Actor with ActorLogging {

  // NOTE: objects/type classes + actors is a bad idea, so ioProcessCallback is used to fulfill that functionality
  //  https://docs.scala-lang.org/overviews/reflection/thread-safety.html

  requestProcessorActor ! operationRequest

  override def receive: Receive = {
    case ioResult: IOResult => () // TODO
    case _ => throw new Exception(":(") // TODO
  }
}