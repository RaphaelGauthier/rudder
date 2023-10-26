module Nodes.DataTypes exposing (..)

import Http exposing (Error)

--
-- All our data types
--

type alias NodeId = { value : String }

type alias Node =
  { id       : NodeId
  , hostname : String
  }

type SortOrder = Asc | Desc

type SortBy
  = Hostname
  | Id
  | PolicyServer
  | Ram
  | AgentVersion
  | Software String
  | NodeProperty String Bool
  | PolicyMode
  | IpAddresses
  | MachineType
  | Kernel
  | Os
  | NodeCompliance
  | LastRun
  | InventoryDate

type alias TableFilters =
  { sortBy    : SortBy
  , sortOrder : SortOrder
  , filter    : String
  }

type alias UI =
  { hasReadRights : Bool
  , loading       : Bool
  , filters       : TableFilters
  , editColumns   : Bool
  , columns       : List SortBy
  }

type alias Model =
  { contextPath : String
  , policyMode  : String
  , nodes       : List Node
  , ui          : UI
  }

type Msg
  = Ignore
  | Copy String
  | CallApi (Model -> Cmd Msg)
  | GetNodes (Result Error (List Node))
  | UpdateUI UI
