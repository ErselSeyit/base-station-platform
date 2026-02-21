{{/*
Standard chart name.
*/}}
{{- define "basestation.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fullname with release prefix.
*/}}
{{- define "basestation.fullname" -}}
{{- if contains .Chart.Name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/*
Chart label value.
*/}}
{{- define "basestation.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels.
*/}}
{{- define "basestation.labels" -}}
helm.sh/chart: {{ include "basestation.chart" .root }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/part-of: basestation-platform
app: {{ .name }}
{{- end }}

{{/*
Selector labels.
*/}}
{{- define "basestation.selectorLabels" -}}
app: {{ .name }}
{{- end }}

{{/*
Build image name from service name.
*/}}
{{- define "basestation.image" -}}
{{- $tag := .svc.imageTag | default .root.Values.global.imageTag }}
{{- printf "%s%s:%s" .root.Values.global.imagePrefix .name $tag }}
{{- end }}

{{/*
Resolve resources: use custom if provided, otherwise look up preset.
*/}}
{{- define "basestation.resources" -}}
{{- if .svc.resources }}
{{- toYaml .svc.resources }}
{{- else }}
{{- $preset := .svc.resourcePreset | default "standard" }}
{{- toYaml (index .root.Values.resourcePresets $preset) }}
{{- end }}
{{- end }}

{{/*
Resolve security context from preset name.
*/}}
{{- define "basestation.securityContext" -}}
{{- $preset := .svc.securityPreset | default "secure" }}
{{- toYaml (index .root.Values.securityPresets $preset) }}
{{- end }}
