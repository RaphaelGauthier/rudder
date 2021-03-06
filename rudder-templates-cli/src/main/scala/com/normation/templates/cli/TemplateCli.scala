/*
*************************************************************************************
* Copyright 2016 Normation SAS
*************************************************************************************
*
* This file is part of Rudder.
*
* Rudder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU General Public License version 3, the copyright holders add
* the following Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
* Public License version 3, when you create a Related Module, this
* Related Module is not considered as a part of the work and may be
* distributed under the license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* Rudder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

*
*************************************************************************************
*/

package com.normation.templates.cli

import java.io.File
import java.io.InputStreamReader
import java.io.BufferedReader
import com.normation.templates.FillTemplatesService
import com.normation.templates.STVariable
import com.normation.utils.Control._
import org.apache.commons.io.FileUtils
import net.liftweb.common._
import net.liftweb.json._
import scopt.OptionParser
import org.apache.commons.io.IOUtils
import java.io.StringWriter

/**
 * The configuration object for our CLI.
 * The basic process is to take one file in input for the definition of variables, one set of files as template to change.
 *
 * By default, the files are generated with the same name in the current folder.
 *
 * - add the possibility to take directories as input (but a good shell can do it, so not very important)
 */

final case class Config(
    variables      : File      = new File("variables.json")
  , templates      : Seq[File] = Seq()
  , outdir         : File      = new File(".")
  , verbose        : Boolean   = false
  , inputExtension : String    = ".st"
  , outputExtension: String    = ""
  , showStackTrace : Boolean   = false
  , outputToStdout : Boolean   = false
)

object Tryor {
  //the lazy param is of course necessary, else the exception is thrown
  //before going to the block, never caught.
  def apply[T](cmd: => T, errorMsg: String): Box[T] = {
    try {
      Full(cmd)
    } catch {
      case ex: Exception => Failure(s"${errorMsg}: ${ex.getMessage}", Full(ex), Empty)
    }
  }
}

object TemplateCli {

  val fillerService = new FillTemplatesService()

  val parser = new OptionParser[Config]("Rudder template cli") {
    head("rudder-templates-cli", "3.3.x")

    opt[File]("outdir") valueName("<file>") action { (x, c) =>
      c.copy(outdir = x) } text("output directory for filled template, default is '.'")

    opt[String]("inext") optional() valueName("<input file extension>") action { (x, c) =>
       c.copy(inputExtension = x) } text("extension of input templates. Default is '.st'")

    opt[String]("outext") optional() valueName("<output file extension>") action { (x, c) =>
       c.copy(outputExtension = x) } text("extension of templates after processing. Default is '' (no extension added)")

    opt[File]('p', "params") optional() valueName("<variable.json>") action { (x, c) =>
      c.copy(variables = x) } text("JSON file defining variables. Default is 'variables.json'. See below for format details.")

    opt[Unit]('X', "stackTrace") optional() action { (_, c) =>
      c.copy(showStackTrace = true) } text("Print stack trace on error")

    opt[Unit]("stdout") optional() action { (_, c) =>
      c.copy(outputToStdout = true) } text("Print stack trace on error")

    arg[File]("<template.st>...") optional() unbounded() action { (x, c) =>
      c.copy(templates = c.templates:+ x) } text("""list of templates to fill. Only file with the correct extension (by default '.st') will
        | be processed. The extension will be replaced by '.cf' by default, ounce processed.""".stripMargin)

    help("help") text("prints this usage text")

    note("""The expected format for variables.json is a simple key:value file, with value being only string, boolean or Array of string. 'system' and 'optioannal' properties can also be specified:
       | {
       |     "key1": true
       |   , "key2": "some value"
       |   , "key3": "42"
       |   , "key4": [ "some", "more", "values", true, false ]
       |   , "key5": { "value": "k5", "system": true, "optional": false }
       |   , "key6": { "value": [ "a1", "a2", "a3" ], "system": false, "optional": true }
       |   , "key7": ""
       |   , "key8": { "value": [] }
       | }
      """.stripMargin)
  }

  def main(args: Array[String]): Unit = {

    //in case of error with args, stop and display usage
    val config = parser.parse(args, Config()).getOrElse {
        parser.showUsage
        System.exit(1)
        //just for type inference, never reached
        Config()
    }

    process(config) match {
      case eb: EmptyBox =>
        val e = eb match {
          case Empty => eb ?~! "Error when processing templates"
          case f:Failure => f
        }
        System.err.println(e.messageChain)
        if(config.showStackTrace) {
          e.rootExceptionCause.foreach { ex =>
            System.err.println (ex.getMessage)
            ex.printStackTrace()
          }
        }
        System.exit(1)

      case Full(res) =>
        //ok
        //here, we can't call System.exit(0), because maven.
        //seriously: http://maven.apache.org/surefire/maven-surefire-plugin/faq.html#vm-termination
        // """Surefire does not support tests or any referenced libraries calling System.exit() at any time."""
    }
  }


  /**
   * An utility method so that I can actually test things,
   * because you know, maven doesn't allow to have exit(1)
   * anywhere, so I'm going to be able to test on Full/Failure
   */
  def process(config: Config) = {
    for {
      variables <- ParseVariables.fromFile(config.variables)
      allDone   <- if(config.templates.nonEmpty) {
                     val filler = //if we are writing to stdout, use a different filler and ignore outputExtension
                                if(config.outputToStdout) {
                                  fillToStdout(variables.toSeq, config.inputExtension) _
                                } else {
                                  fill(variables.toSeq, config.outdir, config.inputExtension, config.outputExtension) _
                                }
                     bestEffort(config.templates) { filler }
                   } else {
                     /*
                      * If no templates are given, try to read from stdin.
                      * In that case, --stdout is forced.
                      */
                     for {
                       content <- readStdin()
                       ok      <- filledAndWriteToStdout(variables.toSeq, content, "stdin")
                     } yield {
                       ok
                     }
                   }
    } yield {
      allDone
    }
  }

  def readStdin(): Box[String] = {
    for {
      in      <- Tryor(new java.io.InputStreamReader(System.in), "Error when trying to access stdin")
      ready   <- if(in.ready) Full("ok") else Failure("Can not get template content from stdin and no template file given")
      content <- Tryor(IOUtils.toString(System.in, "UTF-8"), "Error when trying to read content from stdin")
      ok      <- if(content.length > 0) {
                   Full(content)
                 } else {
                   Failure("Can not get template content from stdin and no template file given")
                 }
    } yield {
      ok
    }
  }

  /**
   * Utility class that handles reading from file / writing to file.
   * It takes variables and outDir as a seperate argument list so that
   * it is easier to reuse the same "filler" context for different templates
   *
   * Only file with inputExtension are processed.
   * inputExtension is replaced by outputExtension.
   */
  def fill(variables: Seq[STVariable], outDir: File, inputExtension: String, outputExtension: String)(template: File): Box[String] = {
    for {
      ok      <- if(template.getName.endsWith(inputExtension)) { Full("ok") } else { Failure(s"Ignoring file ${template.getName} because it does not have extension '${inputExtension}'") }
      content <- Tryor(FileUtils.readFileToString(template), s"Error when reading variables from ${template.getAbsolutePath}")
      filled  <- fillerService.fill(template.getAbsolutePath, content, variables)
      name     = template.getName
      out      = new File(outDir, name.substring(0, name.size-inputExtension.size)+outputExtension)
      writed  <- Tryor(FileUtils.writeStringToFile(out, filled), s"Error when writting filled template into ${out.getAbsolutePath}")
    } yield {
      out.getAbsolutePath
    }
  }

  /**
   * Same as fill, but print everything to stdout
   */
  def fillToStdout(variables: Seq[STVariable], inputExtension: String)(template: File): Box[String] = {
    for {
      ok      <- if(template.getName.endsWith(inputExtension)) { Full("ok") } else { Failure(s"Ignoring file ${template.getName} because it does not have extension '${inputExtension}'") }
      content <- Tryor(FileUtils.readFileToString(template), s"Error when reading variables from ${template.getAbsolutePath}")
      writed  <- filledAndWriteToStdout(variables, content, template.getName)
    } yield {
      writed
    }
  }

  def filledAndWriteToStdout(variables: Seq[STVariable], content: String, templateName: String) = {
    for {
      filled  <- fillerService.fill(templateName, content, variables)
      writed  <- Tryor(IOUtils.write(filled, System.out, "UTF-8"), s"Error when writting filled template to stdout")
    } yield {
      templateName
    }
  }

}

/**
 * Parse the JSON file for variables.
 * We only uderstand two type of value: string and boolean.
 * The expected format is:
 * {
 *     "key1": true
 *   , "key2": "some value"
 *   , "key3": "42"
 *   , "key4": [ "some", "more", "values", true, false ]
 *   , "key5": { "value": "k5", "system": true, "optional": false }
 *   , "key6": { "value": [ "a1", "a2", "a3" ], "system": false, "optional": true }
 *   , "key7": ""
 *   , "key8": { "value": [] }
 * }
 *
 *
 * Default value for system is false
 * Default value for optional is true
 *
 */
object ParseVariables extends Loggable {

  def fromFile(file: File):  Box[Set[STVariable]] = {
    for {
      jsonString <- Tryor(FileUtils.readFileToString(file, "UTF-8"), s"Error when trying to read file ${file.getAbsoluteFile}")
      vars       <- fromString(jsonString)
    } yield {
      vars
    }
  }

  def fromString(jsonString: String): Box[Set[STVariable]] = {

    def parseAsValue(v: JValue): List[Any] = {
      v match {
        case JString(value) => value :: Nil
        case JBool(value)   => value :: Nil
        case JArray(arr)    => arr.map { x => x match {
                                 case JString(value) => value
                                 case JBool(value)   => value
                                 //at that level, any other thing, including array, is parser as a simple string
                                 case value          => compact(render(value))
                               } }
        case value            => compact(render(value)) :: Nil
      }
    }

    //the whole logic
    for {
      json       <- Tryor(JsonParser.parse(jsonString), s"Error when parsing the variable file")
    } yield {

      json.children.flatMap { x =>
        x match {
          case field@JField(name, JObject(values)) => // in that case, only value is mandatory
            val map = values.map { case JField(n, v) => (n, v) }.toMap

            map.get("value") match {
              case None =>
                logger.info(s"Missing mandatory field 'value' in object ${compact(render(field))}")
                None
              case Some(value) =>
                val optional = map.get("optional") match {
                  case Some(JBool(b)) => b
                  case _              => true
                }
                val system = map.get("system") match {
                  case Some(JBool(b)) => b
                  case _              => false
                }

                Some(STVariable(name, optional, parseAsValue(value), system))
            }

          //in any other case, parse as value
          case JField(name, value) => Some(STVariable(name, true, parseAsValue(value), false))

          //and if not a field, well just abort
          case _ => None

        }
      }.toSet
    }
  }
}


