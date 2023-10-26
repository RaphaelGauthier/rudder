module Nodes.ViewUtils exposing (..)

import Html exposing (..)
import Html.Attributes exposing (class, href, attribute, title, style, colspan, rowspan )
import Html.Events exposing (onClick, onInput)
import Json.Decode exposing (decodeValue)
import NaturalOrdering as N exposing (compare)

import Nodes.DataTypes exposing (..)

searchString : String -> String
searchString str = str
  |> String.toLower
  |> String.trim

filterSearch : String -> List String -> Bool
filterSearch filterString searchFields =
  let
    -- Join all the fields into one string to simplify the search
    stringToCheck = searchFields
      |> String.join "|"
      |> String.toLower
  in
    String.contains (searchString filterString) stringToCheck

thClass : TableFilters -> SortBy -> String
thClass tableFilters sortBy =
  if sortBy == tableFilters.sortBy then
    case  tableFilters.sortOrder of
      Asc  -> "sorting_asc"
      Desc -> "sorting_desc"
  else
    "sorting"

sortTable : TableFilters -> SortBy -> TableFilters
sortTable tableFilters sortBy =
  let
    order =
      case tableFilters.sortOrder of
        Asc -> Desc
        Desc -> Asc
  in
    if sortBy == tableFilters.sortBy then
      { tableFilters | sortOrder = order}
    else
      { tableFilters | sortBy = sortBy, sortOrder = Asc}

getSortFunction : Model -> Node -> Node -> Order
getSortFunction model n1 n2 =
  let
    order = case model.ui.filters.sortBy of
      Id       -> N.compare n1.id.value n2.id.value
      Hostname -> N.compare n1.hostname n2.hostname
      _ -> N.compare n1.hostname n2.hostname
      -- TODO : Add all cases
  in
    if model.ui.filters.sortOrder == Asc then
      order
    else
      case order of
        LT -> GT
        EQ -> EQ
        GT -> LT

searchField : Node -> List String
searchField node =
  [ node.id.value
  , node.hostname
  ]

generateLoadingTable : Html Msg
generateLoadingTable =
  div [class "table-container skeleton-loading"]
  [ table [class "dataTable"]
    [ thead []
      [ tr [class "head"]
        [ th [][ span[][] ]
        , th [][ span[][] ]
        , th [][ span[][] ]
        , th [][ span[][] ]
        , th [][ span[][] ]
        ]
      ]
    , tbody []
      [ tr[] [ td[][span[style "width" "45%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[style "width" "30%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[style "width" "75%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[style "width" "45%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[style "width" "70%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[style "width" "80%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[style "width" "30%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[style "width" "75%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[style "width" "45%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[style "width" "70%"][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      , tr[] [ td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]], td[][span[][]] ]
      ]
    ]
  ]

buildNodesTable : Model -> List (Html Msg)
buildNodesTable model =
  let
    nodes = model.nodes
    sortedNodesList = nodes
      |> List.filter (\n -> filterSearch model.ui.filters.filter (searchField n))
      |> List.sortWith (getSortFunction model)

    rowTable : Node -> Html Msg
    rowTable n =
      let
        test = ""
      in
        tr[]
        [ td[][ text n.hostname ]
        , td[][ text n.id.value ]
        ]
  in
    if List.length sortedNodesList > 0 then
      List.map rowTable sortedNodesList
    else
      [ tr[][td [class "empty", colspan 5][i [class "fa fa-exclamation-triangle"][], text "No nodes match your filters."]]]

nodesTableHeader : UI -> Html Msg
nodesTableHeader ui =
  let
    filters = ui.filters
  in
    tr [class "head"]
    [ th [ class (thClass filters Hostname) , rowspan 1, colspan 1
      , onClick (UpdateUI {ui | filters = (sortTable filters Hostname)})
      ] [ text "Hostname" ]
    , th [ class (thClass filters Id) , rowspan 1, colspan 1
      , onClick (UpdateUI {ui | filters = (sortTable filters Id)})
      ] [ text "Id" ]
    ]

allColumns : List SortBy
allColumns =
 [ Hostname
 , Id
 , PolicyServer
 , Ram
 , AgentVersion
 , Software ""
 , NodeProperty "" False
 , PolicyMode
 , IpAddresses
 , MachineType
 , Kernel
 , Os
 , NodeCompliance
 , LastRun
 , InventoryDate
 ]

defaultColumns : List SortBy
defaultColumns =
 [ Hostname
 , PolicyMode
 , Os
 , NodeCompliance
 ]

getColumnTitle : SortBy -> String
getColumnTitle col =
  case col of
    Hostname         -> "Hostname"
    Id               -> "Node ID"
    PolicyServer     -> "Policy server"
    Ram              -> "RAM"
    AgentVersion     -> "Agent version"
    Software _       -> "Software"
    NodeProperty _ _ -> "Property"
    PolicyMode       -> "Policy mode"
    IpAddresses      -> "IP addresses"
    MachineType      -> "Machine type"
    Kernel           -> "Kernel"
    Os               -> "OS"
    NodeCompliance   -> "Compliance"
    LastRun          -> "Last run"
    InventoryDate    -> "Inventory date"