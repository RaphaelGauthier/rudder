port module Rules exposing (..)

import Browser
import DataTypes exposing (..)
import Http exposing (..)
import Init exposing (init, subscriptions)
import View exposing (view)
import Result
import ApiCalls exposing (getRuleDetails, getRulesTree, saveDisableAction)
import List.Extra exposing (remove)
import Random
import UUID

port successNotification : String -> Cmd msg
port errorNotification   : String -> Cmd msg

main =
  Browser.element
    { init = init
    , view = view
    , update = update
    , subscriptions = subscriptions
    }

generator : Random.Generator String
generator = Random.map (UUID.toString) UUID.generator

--
-- update loop --
--
update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
  case msg of
-- utility methods
    -- Generate random id
    GenerateId nextMsg ->
      (model, Random.generate nextMsg generator)
    -- Do an API call
    CallApi call ->
      (model, call model)
    -- neutral element
    Ignore ->
      ( model , Cmd.none)

    -- UI high level stuff: list rules and other elements needed (groups, directives...)
    GetRulesResult res ->
      case  res of
        Ok r ->
            ( { model |
                  rulesTree = r
                , mode = if (model.mode == Loading) then RuleTable else model.mode
              }
              , Cmd.none
             )
        Err err ->
          processApiError "Getting Rules tree" err model
    GetPolicyModeResult res ->
      case res of
        Ok p ->
            ( { model | policyMode = p }
              , Cmd.none
            )
        Err err ->
          processApiError "Getting Policy Mode" err model

    GetGroupsTreeResult res ->
      case res of
        Ok t ->
          ( { model | groupsTree = t }
            , Cmd.none
          )
        Err err ->
          processApiError "Getting Groups tree" err model

    GetTechniquesTreeResult res ->
      case res of
        Ok (t,d) ->
          ( { model | techniquesTree = t, directives = List.concatMap .directives d }
            , Cmd.none
          )
        Err err ->
          processApiError "Getting Directives tree" err model

    ChangeTabFocus newTab ->
      case model.mode of
        EditRule details ->
          ({model | mode = EditRule   {details | tab = newTab}}, Cmd.none)
        _   -> (model, Cmd.none)

    EditDirectives flag ->
      case model.mode of
        EditRule details ->
          ({model | mode = EditRule   {details | editDirectives = flag, tab = Directives}}, Cmd.none)
        _   -> (model, Cmd.none)

    EditGroups flag ->
      case model.mode of
        EditRule details ->
          ({model | mode = EditRule   {details | editGroups = flag, tab = Groups}}, Cmd.none)
        _   -> (model, Cmd.none)

    GetRuleDetailsResult res ->
      case res of
        Ok r ->
          ({model | mode = EditRule (EditRuleDetails (Just r) r Information False False (Tag "" ""))}, Cmd.none)
        Err err ->
          (model, Cmd.none)

    OpenRuleDetails rId ->
      (model, (getRuleDetails model rId))

    OpenCategoryDetails category ->
      ({model | mode = EditCategory (EditCategoryDetails (Just category) category Information )}, Cmd.none)

    CloseDetails ->
      ( { model | mode  = RuleTable } , Cmd.none )

    GetRulesComplianceResult res ->
      case res of
        Ok r ->
          ( { model | rulesCompliance  = r } , Cmd.none )
        Err err ->
          (model, Cmd.none)

    UpdateCategory category ->
      case model.mode of
        EditCategory details   ->
          ({model | mode = EditCategory {details | category = category}}, Cmd.none)
        _   -> (model, Cmd.none)

    SelectGroup groupId includeBool->
      let
        updateTargets : Rule -> Rule
        updateTargets r =
          let
            (include, exclude) = case r.targets of
                [Composition (Or i) (Or e)] -> (i,e)
                targets -> (targets,[])
            isIncluded = List.member groupId include
            isExcluded = List.member groupId exclude
            (newInclude, newExclude)  = case (includeBool, isIncluded, isExcluded) of
              (True, True, _)  -> (remove groupId include,exclude)
              (True, _, True)  -> (groupId :: include, remove groupId exclude)
              (False, True, _) -> (remove groupId include, groupId :: exclude)
              (False, _, True) -> (include,  remove groupId exclude)
              (True, False, False)  -> ( groupId :: include, exclude)
              (False, False, False) -> (include, groupId :: exclude)
          in
            {r | targets = [Composition (Or newInclude) (Or newExclude)]}
      in
        case model.mode of
          EditRule details ->
            ({model | mode = EditRule   {details | rule = (updateTargets details.rule)}}, Cmd.none)
          _   -> (model, Cmd.none)

    UpdateRule rule ->
      case model.mode of
        EditRule details ->
          ({model | mode = EditRule   {details | rule = rule}}, Cmd.none)
        _   -> (model, Cmd.none)

    DisableRule ->
      case model.mode of
        EditRule details ->
          let
            rule     = details.originRule
            cmdAction = case rule of
              Just oR -> saveDisableAction {oR | enabled = not oR.enabled} model
              Nothing -> Cmd.none
          in
            (model, cmdAction)
        _   -> (model, Cmd.none)

    NewRule id ->
      let
        rule        = Rule id "" "rootRuleCategory" "" "" True False [] [] []
        ruleDetails = EditRuleDetails Nothing rule Information False False (Tag "" "")
      in
        ({model | mode = EditRule ruleDetails}, Cmd.none)

    NewCategory id ->
      let
        category        = Category id "" "" (SubCategories []) []
        categoryDetails = EditCategoryDetails Nothing category Information
      in
        ({model | mode = EditCategory categoryDetails}, Cmd.none)

    UpdateNewTag tag ->
      case model.mode of
        EditRule details ->
          ({model | mode = EditRule {details | newTag = tag}}, Cmd.none)
        _   -> (model, Cmd.none)

    SaveRuleDetails (Ok ruleDetails) ->
      case model.mode of
        EditRule details ->
          let
            action = case details.originRule of
              Just oR -> "saved"
              Nothing -> "created"
            newModel = {model | mode = EditRule {details | originRule = Just ruleDetails, rule = ruleDetails}}
          in
            (newModel, Cmd.batch [(successNotification ("Rule '"++ ruleDetails.name ++"' successfully " ++ action))  , (getRulesTree newModel)])
        _   -> (model, Cmd.none )


    SaveRuleDetails (Err err) ->
      processApiError "Saving Rule" err model

    SaveDisableAction (Ok ruleDetails) ->
      case model.mode of
        EditRule details ->
          let
            txtDisable = if ruleDetails.enabled == True then "enabled" else "disabled"
          in
            ({model | mode = EditRule {details | originRule = Just ruleDetails, rule = ruleDetails}}, (Cmd.batch [successNotification ("Rule '"++ ruleDetails.name ++"' successfully "++ txtDisable), (getRulesTree model)]))
        _   -> (model, Cmd.none)

    SaveDisableAction (Err err) ->
      processApiError "Changing rule state" err model

    SaveCategoryResult (Ok category) ->
      case model.mode of
        EditCategory details ->
          let
            oldCategory = details.category
            action      = case details.originCategory of
              Just oC -> "saved"
              Nothing -> "created"
            newCategory = {category | subElems = oldCategory.subElems, elems = oldCategory.elems}
            newModel    = {model | mode = EditCategory {details | originCategory = Just newCategory, category = newCategory}}
          in
            (newModel, Cmd.batch [(successNotification ("Category '"++ category.name ++"' successfully " ++ action)), (getRulesTree newModel)])
        _   -> (model, Cmd.none)

    SaveCategoryResult (Err err) ->
      processApiError "Saving Category" err model

    DeleteRule (Ok (ruleId, ruleName)) ->
      case model.mode of
        EditRule r ->
          let
            newMode  = if r.rule.id == ruleId then RuleTable else model.mode
            newModel = { model | mode = newMode }
          in
            (newModel, Cmd.batch [(successNotification ("Successfully deleted rule '" ++ ruleName ++  "' (id: "++ ruleId.value ++")")), (getRulesTree newModel)])
        _ -> (model, Cmd.none)

    DeleteRule (Err err) ->
      processApiError "Deleting Rule" err model

    DeleteCategory (Ok (categoryId, categoryName)) ->
      case model.mode of
        EditCategory c ->
          let
            newMode  = if c.category.id == categoryId then RuleTable else model.mode
            newModel = { model | mode = newMode }
          in
            (newModel, Cmd.batch [(successNotification ("Successfully deleted category '" ++ categoryName ++  "' (id: "++ categoryId ++")")), (getRulesTree newModel)])
        _ -> (model, Cmd.none)

    DeleteCategory (Err err) ->
      processApiError "Deleting category" err model

    CloneRule rule rulelId ->
      let
        newModel = case model.mode of
          EditRule _ ->
            let
              newRule    = {rule | name = ("Clone of "++rule.name), id = rulelId}
              newRuleDetails = EditRuleDetails Nothing newRule Information False False (Tag "" "")
            in
              { model | mode = EditRule newRuleDetails }
          _ -> model
      in
        (newModel, Cmd.none)

    OpenDeletionPopup rule ->
      case model.mode of
        EditRule _ ->
          let ui = model.ui
          in
            ( { model | ui = {ui | modal = DeletionValidation rule} } , Cmd.none )
        _ -> (model, Cmd.none)

    OpenDeletionPopupCat category ->
      case model.mode of
        EditCategory _ ->
          let ui = model.ui
          in
            ( { model | ui = {ui | modal = DeletionValidationCat category} } , Cmd.none )
        _ -> (model, Cmd.none)

    OpenDeactivationPopup rule ->
      case model.mode of
        EditRule _ ->
          let ui = model.ui
          in
            ( { model | ui = {ui | modal = DeactivationValidation rule}} , Cmd.none )
        _ -> (model, Cmd.none)

    ClosePopup callback ->
      let
        ui = model.ui
        (nm,cmd) = update callback { model | ui = { ui | modal = NoModal } }
      in
        (nm , cmd)

    UpdateRuleFilters sortBy ->
      let
        ui = model.ui
        oldFilters = model.ui.ruleFilters
        filters =
          if sortBy == oldFilters.sortBy then
            {oldFilters | sortOrder = not oldFilters.sortOrder}
          else
            {oldFilters | sortBy = sortBy, sortOrder = True}
      in
        ({model | ui = { ui | ruleFilters = filters}}, Cmd.none)

processApiError : String -> Error -> Model -> ( Model, Cmd Msg )
processApiError apiName err model =
  let
    message =
      case err of
        BadUrl url -> "Wrong url "++ url
        Timeout -> "Request timeout"
        NetworkError -> "Network error"
        BadStatus response -> "Error status: " ++ (String.fromInt response.status.code) ++ " " ++ response.status.message ++
                              "\nError details: " ++ response.body
        BadPayload error response -> "Invalid response: " ++ error ++ "\nResponse Body: " ++ response.body

  in
    ({model | mode = if model.mode == Loading then RuleTable else model.mode}, errorNotification ("Error when "++apiName ++",details: \n" ++ message ) )
