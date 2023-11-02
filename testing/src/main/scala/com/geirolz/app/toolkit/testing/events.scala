package com.geirolz.app.toolkit.testing

import java.util.UUID

sealed trait Event
object Event {
  case class Custom(key: String) extends Event
}
sealed trait LabelEvent extends Event {
  val resource: LabeledResource

  override def toString: String = this match {
    case LabelEvent.Starting(resource)     => s"${resource.label}-starting"
    case LabelEvent.Succeeded(resource)    => s"${resource.label}-succeeded"
    case LabelEvent.Finalized(resource)    => s"${resource.label}-finalized"
    case LabelEvent.Canceled(resource)     => s"${resource.label}-canceled"
    case LabelEvent.Errored(resource, msg) => s"${resource.label}-errored[$msg]"
  }
}
object LabelEvent {
  case class Starting(resource: LabeledResource) extends LabelEvent
  case class Succeeded(resource: LabeledResource) extends LabelEvent
  case class Finalized(resource: LabeledResource) extends LabelEvent
  case class Canceled(resource: LabeledResource) extends LabelEvent
  case class Errored(resource: LabeledResource, msg: String) extends LabelEvent
}

//------------------------------------------------------
case class LabeledResource(label: String, token: UUID) {
  def starting: LabelEvent             = LabelEvent.Starting(this)
  def succeeded: LabelEvent            = LabelEvent.Succeeded(this)
  def finalized: LabelEvent            = LabelEvent.Finalized(this)
  def canceled: LabelEvent             = LabelEvent.Canceled(this)
  def errored(msg: String): LabelEvent = LabelEvent.Errored(this, msg)
}
object LabeledResource {
  private val resourceToken: UUID                                              = UUID.randomUUID()
  private[LabeledResource] def apply(id: String, token: UUID): LabeledResource = new LabeledResource(id, token)
  def resource(id: String): LabeledResource                                    = LabeledResource(id, resourceToken)
  def uniqueResource(id: String): LabeledResource                              = LabeledResource(id, UUID.randomUUID())
  val http: LabeledResource                                                    = LabeledResource.uniqueResource("http")
  val appRuntime: LabeledResource                                              = LabeledResource.uniqueResource("app-runtime")
  val appLoader: LabeledResource                                               = LabeledResource.uniqueResource("app-loader")
  val appDependencies: LabeledResource                                         = LabeledResource.uniqueResource("app-dependencies")
}
