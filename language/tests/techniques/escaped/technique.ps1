# generated by rudderc
# @name escaped
# @version 1.0

function Escaped {
  [CmdletBinding()]
  param (
    [Parameter(Mandatory=$True)]
    [String]$ReportId,
    [Parameter(Mandatory=$True)]
    [String]$TechniqueName,
    [Switch]$AuditOnly
  )

  $ReportIdBase = $reportId.Substring(0,$reportId.Length-1)
  $LocalClasses = New-ClassContext
  $ResourcesDir = $PSScriptRoot + "\resources"
  $ReportId = $ReportIdBase+"3316c616-faec-46e7-b7be-bd5463b47142"
  $LocalClasses = Merge-ClassContext $LocalClasses $(Command-Execution -Command "echo \"Hello de Lu\" > /tmp/myfile-${sys.host}.txt" -ComponentName "Command execution" -ReportId $ReportId -TechniqueName $TechniqueName -Report:$true -AuditOnly:$AuditOnly).get_item("classes")
  $ReportId = $ReportIdBase+"018d891b-9a63-4bf7-b90f-dfc123050b85"
  _rudder_common_report_na -ComponentName "Command execution result" -ComponentKey "rpm -qi gpg-pubkey-\\*|grep -E ^Packager|grep Innoflair" -Message "Not applicable" -ReportId $ReportId -TechniqueName $TechniqueName -Report:$true -AuditOnly:$AuditOnly
  $ReportId = $ReportIdBase+"00c8bb99-805c-4c43-ab58-f9df8eec99c3"
  _rudder_common_report_na -ComponentName "File replace lines" -ComponentKey "/etc/default/grub" -Message "Not applicable" -ReportId $ReportId -TechniqueName $TechniqueName -Report:$true -AuditOnly:$AuditOnly
}
