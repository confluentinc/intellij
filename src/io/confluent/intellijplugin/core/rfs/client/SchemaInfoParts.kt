package io.confluent.intellijplugin.core.rfs.client

import java.io.Serializable
import javax.swing.Icon

/**
 * User: Dmitry.Naydanov
 * Date: 2018-11-23.
 */
interface SchemaInfoPart : Serializable {
  val text: String
  val icon: Icon?
  val onClick: (() -> Unit)?
}