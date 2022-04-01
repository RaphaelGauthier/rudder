# generated by rudderc
# @name 6_2_cis_updated
# @version 1.0

function 6-2-Cis-Updated {
  [CmdletBinding()]
  param (
    [Parameter(Mandatory=$True)]
    [String]$ReportId,
    [Parameter(Mandatory=$True)]
    [String]$TechniqueName,
    [Parameter(Mandatory=$True)]
    [String]$Module,
    [Switch]$AuditOnly
  )

  $ReportIdBase = $reportId.Substring(0,$reportId.Length-1)
  $LocalClasses = New-ClassContext
  $ResourcesDir = $PSScriptRoot + "\resources"
  $ReportId = $ReportIdBase+""
  $LocalClasses = Merge-ClassContext $LocalClasses $(Condition-From-Variable-Existence -Condition "skip_item_${report_data.canonified_directive_id}" -VariableName "node.properties[skip][${report_data.directive_id}]" -ComponentName "Condition from variable existence" -ReportId $ReportId -TechniqueName $TechniqueName -Report:$true -AuditOnly:$AuditOnly).get_item("classes")
  $ReportId = $ReportIdBase+"70d2759a-b3f8-43ca-a515-f005a9be651a"
  _rudder_common_report_na -ComponentName "Kernel module configuration" -ComponentKey "${module}" -Message "Not applicable" -ReportId $ReportId -TechniqueName $TechniqueName -Report:$true -AuditOnly:$AuditOnly
  $ReportId = $ReportIdBase+"449625c9-30a4-4b1a-8e88-f854fe67a108"
  _rudder_common_report_na -ComponentName "Kernel module not loaded" -ComponentKey "${module}" -Message "Not applicable" -ReportId $ReportId -TechniqueName $TechniqueName -Report:$true -AuditOnly:$AuditOnly
}
