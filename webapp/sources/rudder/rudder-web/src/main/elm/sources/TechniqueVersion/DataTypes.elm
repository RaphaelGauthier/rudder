module TechniqueVersion.DataTypes exposing (..)

import Json.Decode as D exposing (..)
--
-- All our data types
--

type alias Technique =
  { version             : String
  , isDeprecated        : Bool
  , deprecationMessage  : String
  , acceptationDate     : String
  , dscSupport          : Bool
  , classicSupport      : Bool
  , multiVersionSupport : String
  , mvsMessage          : String
  }

type alias UI =
  { hasWriteRights    : Bool
  , displayDeprecated : Bool
  }

type alias Model =
  { contextPath    : String
  , ui             : UI
  , techniques     : Maybe (List Technique)
  }

type Msg
  = Ignore String
  | CallApi (Model -> Cmd Msg)
  | Create String
  | ToggleDeprecated Bool
  | GetTechniquesList (Result D.Error (List Technique))