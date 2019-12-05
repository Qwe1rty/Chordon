package service

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import common.utils.ActorDefaults
import schema.ResponseTrait

import scala.concurrent.Promise
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


object RequestActor {

  private def props[A <: ResponseTrait]
      (requestPromise: Promise[A])
      (callback: Option[Array[Byte]] => A)
      (implicit ct: ClassTag[A]): Props = {

    Props(new RequestActor[A](requestPromise)(callback))
  }

  def apply[A <: ResponseTrait]
      (requestPromise: Promise[A], name: String)
      (ioProcessCallback: Option[Array[Byte]] => A)
      (implicit ct: ClassTag[A], parentContext: ActorContext): ActorRef = {

    parentContext.actorOf(props(requestPromise)(ioProcessCallback), name)
  }
}


class RequestActor[+A <: ResponseTrait]
    (requestPromise: Promise[A])
    (ioProcessCallback: Option[Array[Byte]] => A)
    (implicit ct: ClassTag[A])
  extends Actor with ActorLogging with ActorDefaults {

  // NOTE: objects/type classes + actor concurrency is a bad idea, so a callback is used instead
  //  https://docs.scala-lang.org/overviews/reflection/thread-safety.html

  override def receive: Receive = {

    case ioResult: Try[Option[Array[Byte]]] => {
      ioResult match {
        case Success(result) => requestPromise.complete(Try(ioProcessCallback(result)))
        case Failure(e) => requestPromise.failure(e)
      }
      context.stop(self)
    }

    case x => {
      log.error(receivedUnknown(x))
      context.stop(self)
    }
  }
}
