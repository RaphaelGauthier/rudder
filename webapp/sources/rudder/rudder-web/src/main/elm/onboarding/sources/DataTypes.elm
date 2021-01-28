module DataTypes exposing (..)

import Http exposing (Error)
import Dict exposing (..)

type Section
  = Welcome SectionState
  | Account SectionState String String
  | Metrics SectionState Bool Bool
  | GettingStarted SectionState

type SectionState
  = Default
  | Visited
  | Complete
  | Warning

type alias Model =
  { contextPath    : String
  , sections       : Dict Int Section
  , activeSection  : Int
  }

type Msg
  = ChangeActiveSection Int
  | GoToLast



{--
Settings :
  - Username     : String
  - Password     : String
  - Core Metrics : Bool
  - Tech Metrics : Bool

Interface :
  - 4 sections
    - ID :
      - Welcome
      - Account
      - Metrics
      - Getting Started
    - State : (Default | Visited | Complete | Warning)
  - Active Section (ID de section)
  - Validations (peut-être pas besoin de modèle):
    - Username OK
    - Password OK
--}
