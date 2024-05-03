/*
 *************************************************************************************
 * Copyright 2021 Normation SAS
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

package com.normation.rudder.apidata

import com.normation.GitVersion
import com.normation.GitVersion.RevisionInfo
import com.normation.cfclerk.domain.Technique
import com.normation.cfclerk.domain.TechniqueName
import com.normation.cfclerk.domain.TechniqueVersion
import com.normation.errors.*
import com.normation.inventory.domain
import com.normation.inventory.domain.AgentType
import com.normation.inventory.domain.AgentVersion
import com.normation.inventory.domain.FullInventory
import com.normation.inventory.domain.InventoryStatus
import com.normation.inventory.domain.MemorySize
import com.normation.inventory.domain.NodeId
import com.normation.inventory.domain.NodeInventory
import com.normation.inventory.domain.SecurityToken
import com.normation.inventory.domain.SoftwareUpdate
import com.normation.inventory.domain.Version
import com.normation.inventory.domain.VmType
import com.normation.rudder.apidata.JsonResponseObjects.JRPropertyHierarchy.JRPropertyHierarchyHtml
import com.normation.rudder.apidata.JsonResponseObjects.JRPropertyHierarchy.JRPropertyHierarchyJson
import com.normation.rudder.domain.nodes
import com.normation.rudder.domain.nodes.NodeGroup
import com.normation.rudder.domain.nodes.NodeGroupCategoryId
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.nodes.NodeInfo
import com.normation.rudder.domain.nodes.NodeKind
import com.normation.rudder.domain.nodes.NodeState
import com.normation.rudder.domain.policies.*
import com.normation.rudder.domain.properties.GenericProperty
import com.normation.rudder.domain.properties.GlobalParameter
import com.normation.rudder.domain.properties.GroupProperty
import com.normation.rudder.domain.properties.InheritMode
import com.normation.rudder.domain.properties.NodeProperty
import com.normation.rudder.domain.properties.NodePropertyHierarchy
import com.normation.rudder.domain.properties.ParentProperty
import com.normation.rudder.domain.properties.PropertyProvider
import com.normation.rudder.domain.queries.CriterionLine
import com.normation.rudder.domain.queries.Query
import com.normation.rudder.domain.queries.QueryReturnType
import com.normation.rudder.domain.queries.ResultTransformation
import com.normation.rudder.domain.servers.Srv
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.facts.nodes.NodeFact
import com.normation.rudder.facts.nodes.NodeFact.ToCompat
import com.normation.rudder.facts.nodes.SecurityTag
import com.normation.rudder.hooks.Hooks
import com.normation.rudder.ncf.ResourceFile
import com.normation.rudder.ncf.TechniqueParameter
import com.normation.rudder.reports.execution.AgentRunWithNodeConfig
import com.normation.rudder.repository.FullActiveTechnique
import com.normation.rudder.repository.FullActiveTechniqueCategory
import com.normation.rudder.repository.FullNodeGroupCategory
import com.normation.rudder.rule.category.RuleCategory
import com.normation.rudder.rule.category.RuleCategoryId
import com.normation.rudder.services.queries.CmdbQueryParser
import com.normation.rudder.services.queries.StringCriterionLine
import com.normation.rudder.services.queries.StringQuery
import com.normation.rudder.tenants.TenantId
import com.normation.utils.DateFormaterService
import com.softwaremill.quicklens.*
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import enumeratum.Enum
import enumeratum.EnumEntry
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import org.joda.time.DateTime
import zio.*
import zio.Tag as _
import zio.json.*
import zio.json.DeriveJsonEncoder
import zio.json.internal.Write
import zio.syntax.*

/*
 * This class deals with everything serialisation related for API.
 * Change things with care! Everything must be versioned!
 * Even changing a field name can lead to an API incompatible change and
 * so will need a new API version number (and be sure that old behavior is kept
 * for previous versions).
 */

// how to render parent properties in the returned json in node APIs
sealed trait RenderInheritedProperties
object RenderInheritedProperties {
  case object HTML extends RenderInheritedProperties
  case object JSON extends RenderInheritedProperties
}

// to avoid ambiguity with corresponding business objects, we use "JR" as a prfix
object JsonResponseObjects {

  sealed abstract class JRInventoryStatus(val name: String)
  object JRInventoryStatus {
    case object AcceptedInventory extends JRInventoryStatus("accepted")
    case object PendingInventory  extends JRInventoryStatus("pending")
    case object RemovedInventory  extends JRInventoryStatus("deleted")

    implicit val transformer: Transformer[InventoryStatus, JRInventoryStatus] =
      Transformer.derive[InventoryStatus, JRInventoryStatus]
  }

  final case class JRNodeInfo(
      id:          NodeId,
      status:      JRInventoryStatus,
      hostname:    String,
      osName:      String,
      osVersion:   Version,
      machineType: JRNodeDetailLevel.MachineType
  )
  object JRNodeInfo        {
    implicit def transformer(implicit status: InventoryStatus): Transformer[NodeInfo, JRNodeInfo] = Transformer
      .define[NodeInfo, JRNodeInfo]
      .enableBeanGetters
      .withFieldConst(_.status, status.transformInto[JRInventoryStatus])
      .withFieldComputed(_.osName, _.osDetails.os.name)
      .withFieldComputed(_.osVersion, _.osDetails.version)
      .withFieldComputed(
        _.machineType,
        _.machine
          .map(_.machineType)
          .transformInto[JRNodeDetailLevel.MachineType]
      )
      .buildTransformer
  }

  // Same as JRNodeInfo but with optional os and machine details. Mapped from Srv, NodeInfo or FullInventory
  final case class JRNodeChangeStatus(
      id:          NodeId,
      status:      JRInventoryStatus,
      hostname:    String,
      osName:      String,
      osVersion:   Option[Version],
      machineType: Option[JRNodeDetailLevel.MachineType]
  )
  object JRNodeChangeStatus {
    implicit val srvTransformer: Transformer[Srv, JRNodeChangeStatus] =
      Transformer.define[Srv, JRNodeChangeStatus].enableOptionDefaultsToNone.buildTransformer

    implicit def nodeInfoTransformer(implicit status: InventoryStatus): Transformer[NodeInfo, JRNodeChangeStatus] = Transformer
      .define[NodeInfo, JRNodeChangeStatus]
      .enableBeanGetters
      .withFieldConst(_.status, status.transformInto[JRInventoryStatus])
      .withFieldComputed(_.osName, _.osDetails.os.name)
      .withFieldComputed(_.osVersion, n => Some(n.osDetails.version))
      .withFieldComputed(
        _.machineType,
        n => {
          Some(
            n.machine
              .map(_.machineType)
              .transformInto[JRNodeDetailLevel.MachineType]
          )
        }
      )
      .buildTransformer

    implicit def fullInventoryTransformer(implicit status: InventoryStatus): Transformer[FullInventory, JRNodeChangeStatus] = {
      Transformer
        .define[FullInventory, JRNodeChangeStatus]
        .enableBeanGetters
        .withFieldConst(_.status, status.transformInto[JRInventoryStatus])
        .withFieldComputed(_.id, _.node.main.id)
        .withFieldComputed(_.hostname, _.node.main.hostname)
        .withFieldComputed(_.osName, _.node.main.osDetails.os.name)
        .withFieldComputed(_.osVersion, inv => Some(inv.node.main.osDetails.version))
        .withFieldComputed(
          _.machineType,
          inv => {
            Some(
              inv.machine
                .map(_.machineType)
                .transformInto[JRNodeDetailLevel.MachineType]
            )
          }
        )
        .buildTransformer
    }
  }

  // Node details json with all fields optional but minimal fields. Fields are in the same order as in the list of all fields.
  final case class JRNodeDetailLevel(
      // minimal
      id:                          NodeId,
      hostname:                    String,
      status:                      JRInventoryStatus,
      // default
      state:                       Option[NodeState],
      os:                          Option[domain.OsDetails],
      architectureDescription:     Option[String],
      ram:                         Option[MemorySize],
      machine:                     Option[nodes.MachineInfo],
      ipAddresses:                 Option[Chunk[String]],
      description:                 Option[String],
      lastInventoryDate:           Option[DateTime],
      lastRunDate:                 Option[DateTime],
      policyServerId:              Option[NodeId],
      managementTechnology:        Option[Chunk[JRNodeDetailLevel.Management]],
      properties:                  Option[Chunk[JRProperty]],
      policyMode:                  Option[String],
      timezone:                    Option[domain.NodeTimezone],
      tenant:                      Option[TenantId],
      // full
      accounts:                    Option[Chunk[String]],
      bios:                        Option[Chunk[domain.Bios]],
      controllers:                 Option[Chunk[domain.Controller]],
      environmentVariables:        Option[Map[String, String]],
      fileSystems:                 Option[Chunk[domain.FileSystem]],
      managementTechnologyDetails: Option[JRNodeDetailLevel.ManagementDetails],
      memories:                    Option[Chunk[domain.MemorySlot]],
      networkInterfaces:           Option[Chunk[domain.Network]],
      processes:                   Option[Chunk[domain.Process]],
      processors:                  Option[Chunk[domain.Processor]],
      slots:                       Option[Chunk[domain.Slot]],
      software:                    Option[Chunk[domain.Software]],
      softwareUpdate:              Option[Chunk[SoftwareUpdate]],
      sound:                       Option[Chunk[domain.Sound]],
      storage:                     Option[Chunk[domain.Storage]],
      ports:                       Option[Chunk[domain.Port]],
      videos:                      Option[Chunk[domain.Video]],
      virtualMachines:             Option[Chunk[domain.VirtualMachine]]
  )

  object JRNodeDetailLevel {
    implicit def transformer(implicit
        nodeFact: NodeFact,
        status:   InventoryStatus,
        agentRun: Option[AgentRunWithNodeConfig]
    ): Transformer[NodeDetailLevel, JRNodeDetailLevel] = {
      val nodeInfo:    NodeInfo               = nodeFact.toNodeInfo
      val securityTag: Option[SecurityTag]    = nodeFact.rudderSettings.security
      val software:    Chunk[domain.Software] = nodeFact.software.map(_.toSoftware)
      // we could keep inventory as Option to make syntax easier to filter empty lists, or write Some(...).filter(_.nonEmpty) everywhere
      val inventory:   Option[FullInventory]  = Some(nodeFact.toFullInventory)

      Transformer
        .define[NodeDetailLevel, JRNodeDetailLevel]
        .withFieldConst(_.id, nodeInfo.id)
        .withFieldConst(_.hostname, nodeInfo.hostname)
        .withFieldConst(_.status, status.transformInto[JRInventoryStatus])
        // default
        .withFieldComputed(_.state, levelField("state")(nodeInfo.state))
        .withFieldComputed(_.os, levelField("os")(nodeInfo.osDetails))
        .withFieldComputed(_.architectureDescription, levelField(_)("architectureDescription")(nodeInfo.archDescription))
        .withFieldComputed(_.ram, levelField(_)("ram")(nodeInfo.ram))
        .withFieldComputed(_.machine, levelField(_)("machine")(nodeInfo.machine))
        .withFieldComputed(_.ipAddresses, levelField("ipAddresses")(nodeInfo.ips.transformInto[Chunk[String]]))
        .withFieldComputed(_.description, levelField("description")(nodeInfo.description))
        .withFieldComputed(_.lastInventoryDate, levelField("lastInventoryDate")(nodeInfo.inventoryDate))
        .withFieldComputed(
          _.lastRunDate,
          levelField(_)("lastRunDate")(agentRun.map(_.agentRunId.date))
        )
        .withFieldComputed(_.policyServerId, levelField("policyServerId")(nodeInfo.policyServerId))
        .withFieldComputed(
          _.managementTechnology,
          levelField("managementTechnology")(nodeInfo.transformInto[Chunk[Management]])
        )
        .withFieldComputed(
          _.properties,
          levelField("properties")(Chunk.fromIterable(nodeInfo.properties.sortBy(_.name).map(JRProperty.fromNodeProp)))
        )
        .withFieldComputed(_.policyMode, levelField("policyMode")(nodeInfo.policyMode.map(_.name).getOrElse("default")))
        .withFieldComputed(_.timezone, levelField(_)("timezone")(nodeInfo.timezone))
        .withFieldComputed(_.tenant, levelField(_)("tenant")(securityTag.flatMap(_.tenants.headOption)))
        // full
        .withFieldComputed(
          _.accounts,
          levelField(_)("accounts")(inventory.map(_.node.accounts.transformInto[Chunk[String]]).filter(_.nonEmpty))
        )
        .withFieldComputed(
          _.bios,
          levelField(_)("bios")(
            inventory.flatMap(_.machine.map(_.bios.transformInto[Chunk[domain.Bios]])).filter(_.nonEmpty)
          )
        )
        .withFieldComputed(
          _.controllers,
          levelField(_)("controllers")(
            inventory
              .flatMap(_.machine.map(_.controllers.transformInto[Chunk[domain.Controller]]))
              .filter(_.nonEmpty)
          )
        )
        .withFieldComputed(
          _.environmentVariables,
          levelField(_)("environmentVariables")(
            inventory.map(_.node.environmentVariables.groupMapReduce(_.name)(_.value.getOrElse("")) { case (first, _) => first })
          )
        )
        .withFieldComputed(
          _.fileSystems,
          levelField(_)("fileSystems")(
            inventory.map(i => {
              Chunk(domain.FileSystem("none", Some("swap"), totalSpace = i.node.swap)) ++ i.node.fileSystems
                .transformInto[Chunk[domain.FileSystem]]
            })
          )
        )
        .withFieldComputed(
          _.managementTechnologyDetails,
          levelField(_)("managementTechnologyDetails")(inventory.map(_.node.transformInto[ManagementDetails]))
        )
        .withFieldComputed(
          _.memories,
          levelField(_)("memories")(
            inventory
              .flatMap(_.machine.map(_.memories.transformInto[Chunk[domain.MemorySlot]]))
              .filter(_.nonEmpty)
          )
        )
        .withFieldComputed(
          _.networkInterfaces,
          levelField(_)("networkInterfaces")(
            inventory.map(_.node.networks.transformInto[Chunk[domain.Network]])
          )
        )
        .withFieldComputed(
          _.processes,
          levelField(_)("processes")(inventory.map(_.node.processes.transformInto[Chunk[domain.Process]]))
        )
        .withFieldComputed(
          _.processors,
          levelField(_)("processors")(
            inventory.flatMap(_.machine.map(_.processors.transformInto[Chunk[domain.Processor]])).filter(_.nonEmpty)
          )
        )
        .withFieldComputed(
          _.slots,
          levelField(_)("slots")(
            inventory.flatMap(_.machine.map(_.slots.transformInto[Chunk[domain.Slot]])).filter(_.nonEmpty)
          )
        )
        .withFieldComputed(_.software, levelField("software")(software.map(_.transformInto[domain.Software])))
        .withFieldComputed(
          _.softwareUpdate,
          levelField(_)("software")(inventory.map(_.node.softwareUpdates.transformInto[Chunk[domain.SoftwareUpdate]]))
        )
        .withFieldComputed(
          _.sound,
          levelField(_)("sound")(
            inventory.flatMap(_.machine.map(_.sounds.transformInto[Chunk[domain.Sound]])).filter(_.nonEmpty)
          )
        )
        .withFieldComputed(
          _.storage,
          levelField(_)("storage")(
            inventory.flatMap(_.machine.map(_.storages.transformInto[Chunk[domain.Storage]])).filter(_.nonEmpty)
          )
        )
        .withFieldComputed(
          _.ports,
          levelField(_)("ports")(
            inventory.flatMap(_.machine.map(_.ports.transformInto[Chunk[domain.Port]])).filter(_.nonEmpty)
          )
        )
        .withFieldComputed(
          _.videos,
          levelField(_)("videos")(
            inventory.flatMap(_.machine.map(_.videos.transformInto[Chunk[domain.Video]])).filter(_.nonEmpty)
          )
        )
        .withFieldComputed(
          _.virtualMachines,
          levelField(_)("virtualMachines")(
            inventory.map(_.node.vms.transformInto[Chunk[domain.VirtualMachine]]).filter(_.nonEmpty)
          )
        )
        .buildTransformer
    }

    /**
     * Needed to handle the serialization of the "type" of a machine even if it there is no machine.
     */
    sealed abstract class MachineType(override val entryName: String) extends EnumEntry {
      def name: String = entryName
    }

    object MachineType extends Enum[MachineType] {
      case object UnknownMachineType  extends MachineType("Unknown")
      case object PhysicalMachineType extends MachineType("Physical")
      case object VirtualMachineType  extends MachineType("Virtual")
      case object NoMachine           extends MachineType("No machine Inventory")

      def values: IndexedSeq[MachineType] = findValues

      implicit val transformer: Transformer[Option[domain.MachineType], MachineType] = {
        Transformer
          .define[Option[domain.MachineType], MachineType]
          .withCoproductInstance[None.type] { case None => NoMachine }
          .withCoproductInstance[Some[domain.MachineType]] {
            case Some(domain.UnknownMachineType)    => UnknownMachineType
            case Some(domain.PhysicalMachineType)   => PhysicalMachineType
            case Some(_: domain.VirtualMachineType) => VirtualMachineType
          }
          .buildTransformer
      }
    }

    /**
     * The structure does not directly match the domain object, nodeKind is a contextual value of the node added to each AgentInfo
     */
    final case class Management(
        name:         AgentType,
        version:      Option[AgentVersion],
        capabilities: Chunk[String],
        nodeKind:     NodeKind
    )
    object Management {
      implicit val transformer: Transformer[NodeInfo, Chunk[Management]] = (info: NodeInfo) => {
        val agents = info.agentsName.map { agent =>
          val capabilities = agent.capabilities.map(_.value).toList.sorted
          Management(
            agent.agentType,
            agent.version,
            Chunk.fromIterable(capabilities),
            info.nodeKind
          )
        }
        Chunk.fromIterable(agents)
      }
    }

    /**
     * The structure does not directly match the domain object, it aggregates some fields
     */
    final case class ManagementDetails(
        cfengineKeys: Chunk[SecurityToken],
        cfengineUser: String
    )
    object ManagementDetails {
      implicit val transformer: Transformer[NodeInventory, ManagementDetails] = Transformer
        .define[NodeInventory, ManagementDetails]
        .withFieldComputed(_.cfengineKeys, n => Chunk.fromIterable(n.agents.map(_.securityToken)))
        .withFieldComputed(_.cfengineUser, _.main.rootUser)
        .buildTransformer
    }

    // Helpers for more concise syntax of chimney accessor. Different signature are used to avoid abstract type collision
    // levelField("field")(value) for not optional computed value
    private def levelField[A](field: String)(a: => A)(level: NodeDetailLevel):         Option[A] =
      Option.when(level.fields(field))(a)
    // levelField(_)("field")(value) for optional computed value
    private def levelField[A](level: NodeDetailLevel)(field: String)(a: => Option[A]): Option[A] =
      if (level.fields(field)) a else None
  }

  final case class JRActiveTechnique(
      name:     String,
      versions: List[String]
  )

  object JRActiveTechnique {
    def fromTechnique(activeTechnique: FullActiveTechnique): JRActiveTechnique = {
      JRActiveTechnique(activeTechnique.techniqueName.value, activeTechnique.techniques.map(_._1.serialize).toList)
    }
  }

  /*
   * "sections": [
   * {
   *   "section": {
   *     "name": "File to manage",
   *     "sections": [
   *       {
   *         "section": {
   *           "name": "Enforce content by section",
   *           "vars": [
   *             {
   *               "var": {
   *                 "name": "GENERIC_FILE_CONTENT_SECTION_MANAGEMENT",
   *                 "value": "false"
   *               }
   *             },
   *             {
   *               "var": {
   *                 "name": "GENERIC_FILE_SECTION_CONTENT",
   *                 "value": ""
   *               }
   *             },
   *      ],
   *      "vars": [ .... ]
   * .....
   */
  final case class JRDirectiveSectionVar(
      name:  String,
      value: String
  )
  final case class JRDirectiveSection(
      name:     String, // we have one more "var" indirection level between a var and its details:
      // { vars":[ { "var":{ "name": .... } }, { "var": { ... }} ]

      vars:     Option[
        List[Map[String, JRDirectiveSectionVar]]
      ], // we have one more "section" indirection level between a section and its details:
      // { sections":[ { "section":{ "name": .... } }, { "section": { ... }} ]

      sections: Option[List[Map[String, JRDirectiveSection]]]
  ) {

    // toMapVariable is just accumulating var by name in seq, see SectionVal.toMapVariables
    def toMapVariables: Map[String, Seq[String]] = {
      import scala.collection.mutable.Buffer
      import scala.collection.mutable.Map
      val res = Map[String, Buffer[String]]()

      def recToMap(sec: JRDirectiveSection): Unit = {
        sec.vars.foreach(_.foreach(_.foreach {
          case (_, sectionVar) =>
            res.getOrElseUpdate(sectionVar.name, Buffer()).append(sectionVar.value)
        }))
        sec.sections.foreach(_.foreach(_.foreach {
          case (_, section) =>
            recToMap(section)
        }))
      }
      recToMap(this)
      res.map { case (k, buf) => (k, buf.toSeq) }.toMap
    }
  }

  // we have one more level between a directive section and a section
  final case class JRDirectiveSectionHolder(
      section: JRDirectiveSection
  )

  object JRDirectiveSection {
    def fromSectionVal(name: String, sectionVal: SectionVal): JRDirectiveSection = {
      JRDirectiveSection(
        name = name,
        sections = sectionVal.sections.toList.sortBy(_._1) match {
          case Nil  => None
          case list => Some(list.flatMap { case (n, sections) => sections.map(s => Map("section" -> fromSectionVal(n, s))) })
        },
        vars = sectionVal.variables.toList.sortBy(_._1) match {
          case Nil  => None
          case list => Some(list.map { case (n, v) => Map("var" -> JRDirectiveSectionVar(n, v)) })
        }
      )
    }
  }
  final case class JRRevisionInfo(
      revision: String,
      date:     String,
      author:   String,
      message:  String
  )
  object JRRevisionInfo     {
    def fromRevisionInfo(r: RevisionInfo): JRRevisionInfo = {
      JRRevisionInfo(r.rev.value, DateFormaterService.serialize(r.date), r.author, r.message)
    }
  }

  sealed trait JRTechnique    {
    def id:      String
    def name:    String
    def version: String
    def source:  String
  }

  final case class JRBuiltInTechnique(
      name:    String,
      id:      String,
      version: String
  ) extends JRTechnique { val source = "builtin" }

  final case class JRTechniqueParameter(
      id:          String,
      name:        String,
      description: String,
      mayBeEmpty:  Boolean
  )
  object JRTechniqueParameter {
    def from(param: TechniqueParameter): JRTechniqueParameter = {
      JRTechniqueParameter(
        param.id.value,
        param.name,
        param.description.getOrElse(""),
        param.mayBeEmpty
      )
    }
  }

  final case class JRTechniqueResource(
      path:  String,
      state: String
  )
  object JRTechniqueResource  {
    def from(resource: ResourceFile): JRTechniqueResource = {
      JRTechniqueResource(
        resource.path,
        resource.state.value
      )
    }
  }

  final case class JRMethodCallValue(
      name:  String,
      value: String
  )
  final case class JRReportingLogic(
      name:  String,
      value: Option[String]
  )

  final case class JRDirective(
      changeRequestId: Option[String],
      id:              String, // id is in format uid+rev

      displayName:      String,
      shortDescription: String,
      longDescription:  String,
      techniqueName:    String,
      techniqueVersion: String,
      parameters:       Map[String, JRDirectiveSection],
      priority:         Int,
      enabled:          Boolean,
      system:           Boolean,
      policyMode:       String,
      tags:             List[Map[String, String]]
  ) {
    def toDirective(): IOResult[(TechniqueName, Directive)] = {
      for {
        i <- DirectiveId.parse(id).toIO
        v <- TechniqueVersion.parse(techniqueVersion).toIO
        // the Map is just for "section" -> ...
        s <- parameters.get("section").notOptional("Root section entry 'section' is missing for directive parameters")
        m <- PolicyMode.parseDefault(policyMode).toIO
      } yield {
        (
          TechniqueName(techniqueName),
          Directive(
            i,
            v,
            s.toMapVariables,
            displayName,
            shortDescription,
            m,
            longDescription,
            priority,
            enabled,
            system,
            Tags.fromMaps(tags)
          )
        )
      }
    }
  }
  object JRDirective          {
    def empty(id: String): JRDirective =
      JRDirective(None, id, "", "", "", "", "", Map(), 5, enabled = false, system = false, policyMode = "", tags = List())

    def fromDirective(technique: Technique, directive: Directive, crId: Option[ChangeRequestId]): JRDirective = {
      directive
        .into[JRDirective]
        .enableBeanGetters
        .withFieldConst(_.changeRequestId, crId.map(_.value.toString))
        .withFieldComputed(_.id, _.id.serialize)
        .withFieldRenamed(_.name, _.displayName)
        .withFieldConst(_.techniqueName, technique.id.name.value)
        .withFieldComputed(_.techniqueVersion, _.techniqueVersion.serialize)
        .withFieldConst(
          _.parameters,
          Map(
            "section" -> JRDirectiveSection.fromSectionVal(
              SectionVal.ROOT_SECTION_NAME,
              SectionVal.directiveValToSectionVal(technique.rootSection, directive.parameters)
            )
          )
        )
        .withFieldComputed(_.policyMode, _.policyMode.map(_.name).getOrElse("default"))
        .withFieldComputed(_.tags, x => JRTags.fromTags(x.tags))
        .transform
    }
  }

  final case class JRDirectives(directives: List[JRDirective])

  final case class JRDirectiveTreeCategory(
      name:          String,
      description:   String,
      subCategories: List[JRDirectiveTreeCategory],
      techniques:    List[JRDirectiveTreeTechnique]
  )
  object JRDirectiveTreeCategory  {
    def fromActiveTechniqueCategory(technique: FullActiveTechniqueCategory): JRDirectiveTreeCategory = {
      JRDirectiveTreeCategory(
        technique.name,
        technique.description,
        technique.subCategories.map(fromActiveTechniqueCategory),
        technique.activeTechniques.map(JRDirectiveTreeTechnique.fromActiveTechnique)
      )
    }
  }

  final case class JRDirectiveTreeTechnique(
      id:         String,
      name:       String,
      directives: List[JRDirective]
  )
  object JRDirectiveTreeTechnique {
    def fromActiveTechnique(technique: FullActiveTechnique): JRDirectiveTreeTechnique = {
      JRDirectiveTreeTechnique(
        technique.techniqueName.value,
        technique.newestAvailableTechnique.map(_.name).getOrElse(technique.techniqueName.value),
        technique.directives.flatMap { d =>
          technique.techniques.get(d.techniqueVersion).map(t => JRDirective.fromDirective(t, d, None))
        }
      )
    }
  }
  final case class JRApplicationStatus(
      value:   String,
      details: Option[String]
  )
  final case class JRRule(
      changeRequestId: Option[String] = None,
      id:              String, // id is in format uid+rev

      displayName:      String,
      categoryId:       String,
      shortDescription: String,
      longDescription:  String,
      directives:       List[String], // directives ids

      targets:    List[JRRuleTarget],
      enabled:    Boolean,
      system:     Boolean,
      tags:       List[Map[String, String]],
      policyMode: Option[String],
      status:     Option[JRApplicationStatus]
  ) {
    def toRule(): IOResult[Rule] = {
      for {
        i <- RuleId.parse(id).toIO
        d <- ZIO.foreach(directives)(DirectiveId.parse(_).toIO)
      } yield Rule(
        i,
        displayName,
        RuleCategoryId(categoryId),
        targets.map(_.toRuleTarget).toSet,
        d.toSet,
        shortDescription,
        longDescription,
        enabled,
        system,
        Tags.fromMaps(tags)
      )
    }
  }

  object JRRule {
    // create an empty json rule with just ID set
    def empty(id: String): JRRule =
      JRRule(None, id, "", "", "", "", Nil, Nil, enabled = false, system = false, tags = Nil, policyMode = None, status = None)

    // create from a rudder business rule
    def fromRule(
        rule:       Rule,
        crId:       Option[ChangeRequestId],
        policyMode: Option[String],
        status:     Option[(String, Option[String])]
    ): JRRule = {
      rule
        .into[JRRule]
        .enableBeanGetters
        .withFieldConst(_.changeRequestId, crId.map(_.value.toString))
        .withFieldComputed(_.id, _.id.serialize)
        .withFieldRenamed(_.name, _.displayName)
        .withFieldComputed(_.categoryId, _.categoryId.value)
        .withFieldComputed(_.directives, _.directiveIds.map(_.serialize).toList.sorted)
        .withFieldComputed(_.targets, _.targets.toList.sortBy(_.target).map(t => JRRuleTarget(t)))
        .withFieldRenamed(_.isEnabledStatus, _.enabled)
        .withFieldComputed(_.tags, x => JRTags.fromTags(rule.tags))
        .withFieldConst(_.policyMode, policyMode)
        .withFieldConst(_.status, status.map(s => JRApplicationStatus(s._1, s._2)))
        .transform
    }
  }

  object JRTags {
    def fromTags(tags: Tags): List[Map[String, String]] = {
      tags.tags.toList.sortBy(_.name.value).map(t => Map((t.name.value, t.value.value)))
    }
  }

  final case class JRRules(rules: List[JRRule])

  sealed trait JRRuleTarget {
    def toRuleTarget: RuleTarget
  }
  object JRRuleTarget       {
    def apply(t: RuleTarget): JRRuleTarget = {
      def compose(x: TargetComposition): JRRuleTargetComposition = x match {
        case TargetUnion(targets)        => JRRuleTargetComposition.or(x.targets.toList.map(JRRuleTarget(_)))
        case TargetIntersection(targets) => JRRuleTargetComposition.and(x.targets.toList.map(JRRuleTarget(_)))
      }

      t match {
        case x: SimpleTarget      => JRRuleTargetString(x)
        case x: TargetComposition => compose(x)
        case x: TargetExclusion   => JRRuleTargetComposed(compose(x.includedTarget), compose(x.excludedTarget))
      }
    }

    final case class JRRuleTargetString(r: SimpleTarget) extends JRRuleTarget {
      override def toRuleTarget: RuleTarget = r
    }
    final case class JRRuleTargetComposed(
        include: JRRuleTargetComposition,
        exclude: JRRuleTargetComposition
    ) extends JRRuleTarget {
      override def toRuleTarget: RuleTarget = TargetExclusion(include.toRuleTarget, exclude.toRuleTarget)
    }
    sealed trait JRRuleTargetComposition                 extends JRRuleTarget {
      override def toRuleTarget: TargetComposition
    }
    object JRRuleTargetComposition {
      final case class or(list: List[JRRuleTarget])  extends JRRuleTargetComposition {
        override def toRuleTarget: TargetComposition = TargetUnion(list.map(_.toRuleTarget).toSet)
      }
      final case class and(list: List[JRRuleTarget]) extends JRRuleTargetComposition {
        override def toRuleTarget: TargetComposition = TargetUnion(list.map(_.toRuleTarget).toSet)
      }
    }

    implicit val transformer: Transformer[RuleTarget, JRRuleTarget] = apply _
  }

  final case class JRRuleTargetInfo(
      id:                              JRRuleTarget,
      @jsonField("displayName") name:  String,
      description:                     String,
      @jsonField("enabled") isEnabled: Boolean,
      target:                          JRRuleTarget
  )

  object JRRuleTargetInfo {
    implicit val transformer: Transformer[RuleTargetInfo, JRRuleTargetInfo] =
      Transformer.define[RuleTargetInfo, JRRuleTargetInfo].withFieldRenamed(_.target, _.id).buildTransformer
  }

  // CategoryKind is either JRRuleCategory or String (category id)
  // RuleKind is either JRRule or String (rule id)
  final case class JRFullRuleCategory(
      id:          String,
      name:        String,
      description: String,
      parent:      Option[String],
      categories:  List[JRFullRuleCategory],
      rules:       List[JRRule]
  )
  object JRFullRuleCategory {
    /*
     * Prepare for json.
     * Sort field by ID to keep diff easier.
     */
    def fromCategory(
        cat:      RuleCategory,
        allRules: Map[String, Seq[(Rule, Option[String], Option[(String, Option[String])])]],
        parent:   Option[String]
    ): JRFullRuleCategory = {
      cat
        .into[JRFullRuleCategory]
        .withFieldConst(_.parent, parent)
        .withFieldComputed(
          _.categories,
          _.childs.map(c => JRFullRuleCategory.fromCategory(c, allRules, Some(cat.id.value))).sortBy(_.id)
        )
        .withFieldConst(
          _.rules,
          allRules.get(cat.id.value).getOrElse(Nil).map { case (r, p, s) => JRRule.fromRule(r, None, p, s) }.toList.sortBy(_.id)
        )
        .transform
    }
  }

  // when returning a root category, we have a "data":{"ruleCategories":{.... }}. Seems like a bug, though
  final case class JRCategoriesRootEntryFull(ruleCategories: JRFullRuleCategory)
  final case class JRCategoriesRootEntrySimple(ruleCategories: JRSimpleRuleCategory)
  final case class JRCategoriesRootEntryInfo(ruleCategories: JRRuleCategoryInfo)

  final case class JRSimpleRuleCategory(
      id:          String,
      name:        String,
      description: String,
      parent:      String,
      categories:  List[String],
      rules:       List[String]
  )
  object JRSimpleRuleCategory {
    def fromCategory(cat: RuleCategory, parent: String, rules: List[String]): JRSimpleRuleCategory = {
      cat
        .into[JRSimpleRuleCategory]
        .withFieldComputed(_.id, _.id.value)
        .withFieldConst(_.parent, parent)
        .withFieldComputed(_.categories, _.childs.map(_.id.value).sorted)
        .withFieldConst(_.rules, rules)
        .transform
    }
  }

  final case class JRRuleCategoryInfo(
      id:          String,
      name:        String,
      description: String,
      parent:      Option[String],
      categories:  List[JRRuleCategoryInfo],
      rules:       List[JRRuleInfo]
  )
  object JRRuleCategoryInfo   {
    def fromCategory(
        cat:      RuleCategory,
        allRules: Map[RuleCategoryId, Seq[Rule]],
        parent:   Option[String]
    ): JRRuleCategoryInfo = {
      cat
        .into[JRRuleCategoryInfo]
        .withFieldConst(_.parent, parent)
        .withFieldComputed(
          _.categories,
          _.childs.map(c => JRRuleCategoryInfo.fromCategory(c, allRules, Some(cat.id.value))).sortBy(_.id)
        )
        .withFieldConst(
          _.rules,
          allRules.get(cat.id).getOrElse(Nil).map(JRRuleInfo.fromRule).toList.sortBy(_.id)
        )
        .transform
    }
  }

  final case class JRRuleInfo(
      id:               String,
      displayName:      String,
      categoryId:       String,
      shortDescription: String,
      longDescription:  String,
      enabled:          Boolean,
      tags:             List[Map[String, String]]
  )
  object JRRuleInfo           {
    def fromRule(rule: Rule): JRRuleInfo = {
      rule
        .into[JRRuleInfo]
        .enableBeanGetters
        .withFieldComputed(_.id, _.id.serialize)
        .withFieldRenamed(_.name, _.displayName)
        .withFieldComputed(_.categoryId, _.categoryId.value)
        .withFieldComputed(_.tags, x => JRTags.fromTags(rule.tags))
        .transform
    }
  }

  final case class JRGlobalParameter(
      changeRequestId: Option[String] = None,
      id:              String,
      value:           ConfigValue,
      description:     String,
      inheritMode:     Option[InheritMode],
      provider:        Option[PropertyProvider]
  )

  object JRGlobalParameter {
    import GenericProperty.*
    def empty(name: String): JRGlobalParameter = JRGlobalParameter(None, name, "".toConfigValue, "", None, None)
    def fromGlobalParameter(p: GlobalParameter, crId: Option[ChangeRequestId]): JRGlobalParameter = {
      JRGlobalParameter(crId.map(_.value.toString), p.name, p.value, p.description, p.inheritMode, p.provider)
    }
  }

  final case class JRPropertyHierarchyStatus(
      hasChildTypeConflicts: Boolean,
      fullHierarchy:         List[JRParentPropertyDetails]
  )

  @jsonDiscriminator("kind") sealed trait JRParentPropertyDetails {
    def valueType: String
  }

  object JRParentPropertyDetails {
    @jsonHint("global")
    final case class JRParentGlobalDetails(
        valueType: String
    ) extends JRParentPropertyDetails
    @jsonHint("group")
    final case class JRParentGroupDetails(
        name:      String,
        id:        String,
        valueType: String
    ) extends JRParentPropertyDetails
    @jsonHint("node")
    final case class JRParentNodeDetails(
        name:      String,
        id:        String,
        valueType: String
    ) extends JRParentPropertyDetails

    def fromParentProperty(p: ParentProperty): JRParentPropertyDetails = {
      def serializeValueType(v: ConfigValue): String = v.valueType.name().toLowerCase().capitalize
      p match {
        case ParentProperty.Group(name, id, value) =>
          JRParentGroupDetails(name, id.serialize, serializeValueType(value))
        case ParentProperty.Node(name, id, value)  =>
          JRParentNodeDetails(name, id.value, serializeValueType(value))
        case ParentProperty.Global(value)          =>
          JRParentGlobalDetails(serializeValueType(value))
      }
    }
  }

  // similar to JRGlobalParameter but s/id/name and no changeRequestId
  final case class JRProperty(
      name:            String,
      value:           ConfigValue,
      description:     Option[String],
      inheritMode:     Option[InheritMode],
      provider:        Option[PropertyProvider],
      hierarchy:       Option[JRPropertyHierarchy],
      hierarchyStatus: Option[JRPropertyHierarchyStatus],
      origval:         Option[ConfigValue]
  )
  object JRProperty {
    def fromGroupProp(p: GroupProperty): JRProperty = {
      val desc = if (p.description.trim.isEmpty) None else Some(p.description)
      JRProperty(p.name, p.value, desc, p.inheritMode, p.provider, None, None, None)
    }

    def fromNodeProp(p: NodeProperty): JRProperty = {
      val desc = if (p.description.trim.isEmpty) None else Some(p.description)
      JRProperty(p.name, p.value, desc, p.inheritMode, p.provider, None, None, None)
    }

    def fromNodePropertyHierarchy(
        prop:                  NodePropertyHierarchy,
        hasChildTypeConflicts: Boolean,
        fullHierarchy:         List[ParentProperty],
        renderInHtml:          RenderInheritedProperties
    ): JRProperty = {
      val (parents, origval) = prop.hierarchy.reverse match {
        case Nil  => (None, None)
        case list =>
          val parents = renderInHtml match {
            case RenderInheritedProperties.HTML =>
              JRPropertyHierarchyHtml(
                list
                  .map(p =>
                    s"<p>from <b>${p.displayName}</b>:<pre>${p.value.render(ConfigRenderOptions.defaults().setOriginComments(false))}</pre></p>"
                  )
                  .mkString("")
              )
            case RenderInheritedProperties.JSON =>
              JRPropertyHierarchyJson(list.map(JRParentProperty.fromParentProperty(_)))
          }
          (Some(parents), prop.hierarchy.headOption.map(_.value))
      }
      val hierarchyStatus    = Some(
        JRPropertyHierarchyStatus(
          hasChildTypeConflicts,
          fullHierarchy.map(JRParentPropertyDetails.fromParentProperty(_))
        )
      )
      val desc               = if (prop.prop.description.trim.isEmpty) None else Some(prop.prop.description)
      JRProperty(
        prop.prop.name,
        prop.prop.value,
        desc,
        prop.prop.inheritMode,
        prop.prop.provider,
        parents,
        hierarchyStatus,
        origval
      )
    }
  }

  @jsonDiscriminator("kind") sealed trait JRParentProperty { def value: ConfigValue }
  object JRParentProperty                                  {
    @jsonHint("global")
    final case class JRParentGlobal(
        value: ConfigValue
    ) extends JRParentProperty

    @jsonHint("group")
    final case class JRParentGroup(
        name:  String,
        id:    String,
        value: ConfigValue
    ) extends JRParentProperty

    def fromParentProperty(p: ParentProperty): JRParentProperty = {
      p match {
        case ParentProperty.Group(name, id, value) =>
          JRParentGroup(name, id.serialize, value)
        case _                                     =>
          JRParentGlobal(p.value)
      }
    }
  }

  sealed trait JRPropertyHierarchy extends Product
  object JRPropertyHierarchy {
    final case class JRPropertyHierarchyHtml(html: String)                    extends JRPropertyHierarchy
    final case class JRPropertyHierarchyJson(parents: List[JRParentProperty]) extends JRPropertyHierarchy
  }

  final case class JRGroupInheritedProperties(
      groupId:    String,
      properties: List[JRProperty]
  )

  object JRGroupInheritedProperties {
    def fromGroup(
        groupId:    NodeGroupId,
        properties: List[
          (NodePropertyHierarchy, List[ParentProperty], Boolean)
        ], // parent properties, child properties, has conflicts
        renderInHtml: RenderInheritedProperties
    ): JRGroupInheritedProperties = {
      JRGroupInheritedProperties(
        groupId.serialize,
        properties
          .sortBy(_._1.prop.name)
          .map {
            case (parentProperties, childProperties, hasConflicts) =>
              JRProperty.fromNodePropertyHierarchy(parentProperties, hasConflicts, childProperties, renderInHtml)
          }
      )
    }
  }

  final case class JRCriterium(
      objectType: String,
      attribute:  String,
      comparator: String,
      value:      String
  ) {
    def toStringCriterionLine: StringCriterionLine = StringCriterionLine(objectType, attribute, comparator, Some(value))
  }

  object JRCriterium {
    def fromCriterium(c: CriterionLine): JRCriterium = {
      c.into[JRCriterium]
        .withFieldComputed(_.objectType, _.objectType.objectType)
        .withFieldComputed(_.attribute, _.attribute.name)
        .withFieldComputed(_.comparator, _.comparator.id)
        .transform
    }
  }

  final case class JRQuery(
      select:      String,
      composition: String,
      transform:   Option[String],
      where:       List[JRCriterium]
  )

  object JRQuery {
    def fromQuery(query: Query): JRQuery = {
      JRQuery(
        query.returnType.value,
        query.composition.value,
        query.transform match {
          case ResultTransformation.Identity => None
          case x                             => Some(x.value)
        },
        query.criteria.map(JRCriterium.fromCriterium(_))
      )
    }
  }

  final case class JRGroup(
      changeRequestId: Option[String] = None,
      id:              String,
      displayName:     String,
      description:     String,
      category:        String,
      query:           Option[JRQuery],
      nodeIds:         List[String],
      dynamic:         Boolean,
      enabled:         Boolean,
      groupClass:      List[String],
      properties:      List[JRProperty],
      target:          String,
      system:          Boolean
  ) {
    def toGroup(queryParser: CmdbQueryParser): IOResult[(NodeGroupCategoryId, NodeGroup)] = {
      for {
        i <- NodeGroupId.parse(id).toIO
        q <- query match {
               case None    => None.succeed
               case Some(q) =>
                 for {
                   t <- QueryReturnType(q.select).toIO
                   x <- queryParser
                          .parse(StringQuery(t, Some(q.composition), q.transform, q.where.map(_.toStringCriterionLine)))
                          .toIO
                 } yield Some(x)
             }
      } yield {
        (
          NodeGroupCategoryId(category),
          NodeGroup(
            i,
            displayName,
            description,
            properties.map(p => GroupProperty(p.name, GitVersion.DEFAULT_REV, p.value, p.inheritMode, p.provider)),
            q,
            dynamic,
            nodeIds.map(NodeId(_)).toSet,
            enabled,
            system
          )
        )
      }
    }
  }

  object JRGroup {
    def empty(id: String): JRGroup = JRGroup(
      None,
      id,
      "",
      "",
      "",
      None,
      Nil,
      dynamic = false,
      enabled = false,
      groupClass = Nil,
      properties = Nil,
      target = "",
      system = false
    )

    def fromGroup(group: NodeGroup, catId: NodeGroupCategoryId, crId: Option[ChangeRequestId]): JRGroup = {
      group
        .into[JRGroup]
        .enableBeanGetters
        .withFieldConst(_.changeRequestId, crId.map(_.value.toString))
        .withFieldComputed(_.id, _.id.serialize)
        .withFieldRenamed(_.name, _.displayName)
        .withFieldConst(_.category, catId.value)
        .withFieldComputed(_.query, _.query.map(JRQuery.fromQuery(_)))
        .withFieldComputed(_.nodeIds, _.serverList.toList.map(_.value).sorted)
        .withFieldComputed(_.groupClass, x => List(x.id.serialize, x.name).map(RuleTarget.toCFEngineClassName _).sorted)
        .withFieldComputed(_.properties, _.properties.map(JRProperty.fromGroupProp(_)))
        .withFieldComputed(_.target, x => GroupTarget(x.id).target)
        .withFieldComputed(_.system, _.isSystem)
        .transform
    }
  }

  /**
   * Representation of a group category with bare minimum group information
   */
  final case class JRGroupCategoryInfo(
      id:                                     String,
      name:                                   String,
      description:                            String,
      @jsonField("categories") subCategories: List[JRGroupCategoryInfo],
      groups:                                 List[JRGroupCategoryInfo.JRGroupInfo],
      @jsonField("targets") targetInfos:      List[JRRuleTargetInfo]
  )

  object JRGroupCategoryInfo {
    final case class JRGroupInfo(
        id:                              NodeGroupId,
        @jsonField("displayName") name:  String,
        description:                     String,
        category:                        Option[NodeGroupCategoryId],
        @jsonField("dynamic") isDynamic: Boolean,
        @jsonField("enabled") isEnabled: Boolean,
        target:                          String
    )
    object JRGroupInfo {
      implicit def transformer(implicit categoryId: Option[NodeGroupCategoryId]): Transformer[NodeGroup, JRGroupInfo] = {
        Transformer
          .define[NodeGroup, JRGroupInfo]
          .enableBeanGetters
          .withFieldConst(_.category, categoryId)
          .withFieldComputed(_.target, x => GroupTarget(x.id).target)
          .buildTransformer
      }

    }

    implicit lazy val transformer: Transformer[FullNodeGroupCategory, JRGroupCategoryInfo] = {
      Transformer
        .define[FullNodeGroupCategory, JRGroupCategoryInfo]
        .withFieldComputed(
          _.subCategories,
          _.subCategories.sortBy(_.id.value).transformInto[List[JRGroupCategoryInfo]]
        )
        .withFieldComputed(
          _.groups,
          cat => {
            cat.ownGroups.values.toList.map(t => {
              implicit val categoryId: Option[NodeGroupCategoryId] = cat.categoryByGroupId.get(t.nodeGroup.id)
              t.nodeGroup.transformInto[JRGroupInfo]
            })
          }
        )
        .withFieldComputed(
          _.targetInfos,
          _.targetInfos.collect {
            case t @ FullRuleTargetInfo(_: FullOtherTarget, _, _, _, _) => t.toTargetInfo.transformInto[JRRuleTargetInfo]
          }
        )
        .buildTransformer
    }
  }

  final case class JRRuleNodesDirectives(
      id: String, // id is in format uid+rev

      numberOfNodes:      Int,
      numberOfDirectives: Int
  )

  object JRRuleNodesDirectives {
    // create an empty json rule with just ID set
    def empty(id: String): JRRuleNodesDirectives = JRRuleNodesDirectives(id, 0, 0)

    // create from a rudder business rule
    def fromData(ruleId: RuleId, nodesCount: Int, directivesCount: Int): JRRuleNodesDirectives = {
      JRRuleNodesDirectives(ruleId.serialize, nodesCount, directivesCount)
    }
  }

  final case class JRHooks(
      basePath:  String,
      hooksFile: List[String]
  )

  object JRHooks {
    def fromHook(hook: Hooks): JRHooks = {
      hook
        .into[JRHooks]
        .withFieldConst(_.basePath, hook.basePath)
        .withFieldConst(_.hooksFile, hook.hooksFile.map(_._1))
        .transform
    }
  }

  implicit def seqToChunkTransformer[A, B](implicit transformer: Transformer[A, B]): Transformer[Seq[A], Chunk[B]] = {
    (a: Seq[A]) => Chunk.fromIterable(a.map(transformer.transform))
  }
}
//////////////////////////// zio-json encoders ////////////////////////////

trait RudderJsonEncoders {
  import JsonResponseObjects.*
  import JsonResponseObjects.JRNodeDetailLevel.*
  import JsonResponseObjects.JRRuleTarget.*
  import com.normation.inventory.domain.JsonSerializers.implicits.*
  import com.normation.rudder.facts.nodes.NodeFactSerialisation.*
  import com.normation.rudder.facts.nodes.NodeFactSerialisation.SimpleCodec.*
  import com.normation.utils.DateFormaterService.json.*

  implicit lazy val stringTargetEnc: JsonEncoder[JRRuleTargetString]          = JsonEncoder[String].contramap(_.r.target)
  implicit lazy val andTargetEnc:    JsonEncoder[JRRuleTargetComposition.or]  = JsonEncoder[List[JRRuleTarget]].contramap(_.list)
  implicit lazy val orTargetEnc:     JsonEncoder[JRRuleTargetComposition.and] = JsonEncoder[List[JRRuleTarget]].contramap(_.list)
  implicit lazy val comp1TargetEnc:  JsonEncoder[JRRuleTargetComposed]        = DeriveJsonEncoder.gen
  implicit lazy val comp2TargetEnc:  JsonEncoder[JRRuleTargetComposition]     = DeriveJsonEncoder.gen
  implicit lazy val targetEncoder:   JsonEncoder[JRRuleTarget]                = new JsonEncoder[JRRuleTarget] {
    override def unsafeEncode(a: JRRuleTarget, indent: Option[Int], out: Write): Unit = {
      a match {
        case x: JRRuleTargetString      => stringTargetEnc.unsafeEncode(x, indent, out)
        case x: JRRuleTargetComposed    => comp1TargetEnc.unsafeEncode(x, indent, out)
        case x: JRRuleTargetComposition => comp2TargetEnc.unsafeEncode(x, indent, out)
      }
    }
  }

  implicit val ruleTargetInfoEncoder: JsonEncoder[JRRuleTargetInfo] = DeriveJsonEncoder.gen[JRRuleTargetInfo]

  implicit val ruleIdEncoder:          JsonEncoder[RuleId]              = JsonEncoder[String].contramap(_.serialize)
  implicit val groupIdEncoder:         JsonEncoder[NodeGroupId]         = JsonEncoder[String].contramap(_.serialize)
  implicit val groupCategoryIdEncoder: JsonEncoder[NodeGroupCategoryId] = JsonEncoder[String].contramap(_.value)

  implicit val applicationStatusEncoder: JsonEncoder[JRApplicationStatus] = DeriveJsonEncoder.gen

  implicit val ruleEncoder: JsonEncoder[JRRule]  = DeriveJsonEncoder.gen
  implicit val hookEncoder: JsonEncoder[JRHooks] = DeriveJsonEncoder.gen

  implicit val ruleNodesDirectiveEncoder: JsonEncoder[JRRuleNodesDirectives] = DeriveJsonEncoder.gen
  implicit val ruleInfoEncoder:           JsonEncoder[JRRuleInfo]            = DeriveJsonEncoder.gen

  implicit val simpleCategoryEncoder:    JsonEncoder[JRSimpleRuleCategory]        = DeriveJsonEncoder.gen
  implicit lazy val fullCategoryEncoder: JsonEncoder[JRFullRuleCategory]          = DeriveJsonEncoder.gen
  implicit lazy val infoCategoryEncoder: JsonEncoder[JRRuleCategoryInfo]          = DeriveJsonEncoder.gen
  implicit val rootCategoryEncoder1:     JsonEncoder[JRCategoriesRootEntryFull]   = DeriveJsonEncoder.gen
  implicit val rootCategoryEncoder2:     JsonEncoder[JRCategoriesRootEntrySimple] = DeriveJsonEncoder.gen
  implicit val rootCategoryEncoder3:     JsonEncoder[JRCategoriesRootEntryInfo]   = DeriveJsonEncoder.gen

  implicit val rulesEncoder: JsonEncoder[JRRules] = DeriveJsonEncoder.gen

  implicit val directiveSectionVarEncoder:         JsonEncoder[JRDirectiveSectionVar]    = DeriveJsonEncoder.gen
  implicit lazy val directiveSectionHolderEncoder: JsonEncoder[JRDirectiveSectionHolder] = DeriveJsonEncoder.gen
  implicit lazy val directiveSectionEncoder:       JsonEncoder[JRDirectiveSection]       = DeriveJsonEncoder.gen
  implicit val directiveEncoder:                   JsonEncoder[JRDirective]              = DeriveJsonEncoder.gen
  implicit val directivesEncoder:                  JsonEncoder[JRDirectives]             = DeriveJsonEncoder.gen
  implicit val directiveTreeTechniqueEncoder:      JsonEncoder[JRDirectiveTreeTechnique] = DeriveJsonEncoder.gen
  implicit val directiveTreeEncoder:               JsonEncoder[JRDirectiveTreeCategory]  = DeriveJsonEncoder.gen

  implicit val activeTechniqueEncoder: JsonEncoder[JRActiveTechnique] = DeriveJsonEncoder.gen

  implicit val configValueEncoder:      JsonEncoder[ConfigValue]              = new JsonEncoder[ConfigValue] {
    override def unsafeEncode(a: ConfigValue, indent: Option[Int], out: Write): Unit = {
      val options = ConfigRenderOptions.concise().setJson(true).setFormatted(indent.isDefined)
      out.write(a.render(options))
    }
  }
  implicit val propertyProviderEncoder: JsonEncoder[Option[PropertyProvider]] = JsonEncoder[Option[String]].contramap {
    case None | Some(PropertyProvider.defaultPropertyProvider) => None
    case Some(x)                                               => Some(x.value)
  }
  implicit val inheritModeEncoder:      JsonEncoder[InheritMode]              = JsonEncoder[String].contramap(_.value)
  implicit val globalParameterEncoder:  JsonEncoder[JRGlobalParameter]        = DeriveJsonEncoder
    .gen[JRGlobalParameter]
    .contramap(g => {
      // when inheritMode or property provider are set to their default value, don't write them
      g.modify(_.inheritMode)
        .using {
          case Some(InheritMode.Default) => None
          case x                         => x
        }
        .modify(_.provider)
        .using {
          case Some(PropertyProvider.defaultPropertyProvider) => None
          case x                                              => x
        }
    })

  implicit val propertyJRParentProperty: JsonEncoder[JRParentProperty] = DeriveJsonEncoder.gen

  implicit val propertyHierarchyEncoder:        JsonEncoder[JRPropertyHierarchy]        = new JsonEncoder[JRPropertyHierarchy] {
    override def unsafeEncode(a: JRPropertyHierarchy, indent: Option[Int], out: Write): Unit = {
      a match {
        case JRPropertyHierarchy.JRPropertyHierarchyJson(parents) =>
          JsonEncoder[List[JRParentProperty]].unsafeEncode(parents, indent, out)
        case JRPropertyHierarchy.JRPropertyHierarchyHtml(html)    =>
          JsonEncoder[String].unsafeEncode(html, indent, out)
      }
    }
  }
  implicit val propertyDetailsEncoder:          JsonEncoder[JRParentPropertyDetails]    = DeriveJsonEncoder.gen
  implicit val propertyHierarchyStatusEncoder:  JsonEncoder[JRPropertyHierarchyStatus]  = DeriveJsonEncoder.gen
  implicit val propertyEncoder:                 JsonEncoder[JRProperty]                 = DeriveJsonEncoder.gen
  implicit val criteriumEncoder:                JsonEncoder[JRCriterium]                = DeriveJsonEncoder.gen
  implicit val queryEncoder:                    JsonEncoder[JRQuery]                    = DeriveJsonEncoder.gen
  implicit val groupEncoder:                    JsonEncoder[JRGroup]                    = DeriveJsonEncoder.gen
  implicit val objectInheritedObjectProperties: JsonEncoder[JRGroupInheritedProperties] = DeriveJsonEncoder.gen

  implicit val groupInfoEncoder:              JsonEncoder[JRGroupCategoryInfo.JRGroupInfo] =
    DeriveJsonEncoder.gen[JRGroupCategoryInfo.JRGroupInfo]
  implicit lazy val groupCategoryInfoEncoder: JsonEncoder[JRGroupCategoryInfo]             = DeriveJsonEncoder.gen[JRGroupCategoryInfo]

  implicit val revisionInfoEncoder: JsonEncoder[JRRevisionInfo] = DeriveJsonEncoder.gen

  implicit val nodeIdEncoder: JsonEncoder[NodeId]            = JsonEncoder[String].contramap(_.value)
  implicit val statusEncoder: JsonEncoder[JRInventoryStatus] = JsonEncoder[String].contramap(_.name)
  implicit val stateEncoder:  JsonEncoder[NodeState]         = JsonEncoder[String].contramap(_.name)

  implicit val machineTypeEncoder: JsonEncoder[MachineType] = JsonEncoder[String].contramap(_.name)

  implicit val nodeInfoEncoder: JsonEncoder[JRNodeInfo] = DeriveJsonEncoder.gen[JRNodeInfo]

  implicit val nodeChangeStatusEncoder: JsonEncoder[JRNodeChangeStatus] = DeriveJsonEncoder.gen[JRNodeChangeStatus]

  // Node details

  implicit val vmTypeEncoder: JsonEncoder[VmType] = JsonEncoder[String].contramap(_.name)

  implicit val agentTypeEncoder:         JsonEncoder[AgentType]           = JsonEncoder[String].contramap(_.displayName)
  implicit val agentVersionEncoder:      JsonEncoder[AgentVersion]        = JsonEncoder[String].contramap(_.value)
  implicit val nodeKindEncoder:          JsonEncoder[NodeKind]            = JsonEncoder[String].contramap(_.name)
  implicit val securityTokenEncoder:     JsonEncoder[SecurityToken]       = JsonEncoder[String].contramap(_.key)
  implicit val managementEncoder:        JsonEncoder[Management]          = DeriveJsonEncoder.gen[Management]
  implicit val managementDetailsEncoder: JsonEncoder[ManagementDetails]   = DeriveJsonEncoder.gen[ManagementDetails]
  implicit val licenseEncoder:           JsonEncoder[domain.License]      = DeriveJsonEncoder.gen[domain.License]
  implicit val softwareUuidEncoder:      JsonEncoder[domain.SoftwareUuid] = JsonEncoder[String].contramap(_.value)
  implicit val softwareEncoder:          JsonEncoder[domain.Software]     = DeriveJsonEncoder.gen[domain.Software]

  implicit val nodeDetailLevelEncoder: JsonEncoder[JRNodeDetailLevel] = DeriveJsonEncoder.gen[JRNodeDetailLevel]

}

/*
 * Decoders for JsonResponse object, when you need to read back something that they serialized.
 */
object JsonResponseObjectDecodes extends RudderJsonDecoders {
  import JsonResponseObjects.*

  implicit lazy val decodeJRParentProperty:          JsonDecoder[JRParentProperty]          = DeriveJsonDecoder.gen
  implicit lazy val decodeJRPropertyHierarchy:       JsonDecoder[JRPropertyHierarchy]       = DeriveJsonDecoder.gen
  implicit lazy val decodePropertyProvider:          JsonDecoder[PropertyProvider]          = JsonDecoder.string.map(s => PropertyProvider(s))
  implicit lazy val decodeJRParentPropertyDetails:   JsonDecoder[JRParentPropertyDetails]   = DeriveJsonDecoder.gen
  implicit lazy val decodeJRPropertyHierarchyStatus: JsonDecoder[JRPropertyHierarchyStatus] = DeriveJsonDecoder.gen
  implicit lazy val decodeJRProperty:                JsonDecoder[JRProperty]                = DeriveJsonDecoder.gen

  implicit lazy val decodeJRCriterium:           JsonDecoder[JRCriterium]           = DeriveJsonDecoder.gen
  implicit lazy val decodeJRDirectiveSectionVar: JsonDecoder[JRDirectiveSectionVar] = DeriveJsonDecoder.gen

  implicit lazy val decodeJRApplicationStatus: JsonDecoder[JRApplicationStatus] = DeriveJsonDecoder.gen
  implicit lazy val decodeJRQuery:             JsonDecoder[JRQuery]             = DeriveJsonDecoder.gen
  implicit lazy val decodeJRDirectiveSection:  JsonDecoder[JRDirectiveSection]  = DeriveJsonDecoder.gen
  implicit lazy val decodeJRRule:              JsonDecoder[JRRule]              = DeriveJsonDecoder.gen
  implicit lazy val decodeJRGroup:             JsonDecoder[JRGroup]             = DeriveJsonDecoder.gen
  implicit lazy val decodeJRDirective:         JsonDecoder[JRDirective]         = DeriveJsonDecoder.gen

}
