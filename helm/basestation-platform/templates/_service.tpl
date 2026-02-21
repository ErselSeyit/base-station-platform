{{/*
Generic service template.
Expects dict with keys: root (global context), name (string), svc (service values)
*/}}
{{- define "basestation.service" -}}
{{- if ne (.svc.createService | toString) "false" }}
apiVersion: v1
kind: Service
metadata:
  name: {{ .name }}
  namespace: {{ .root.Release.Namespace }}
  labels:
    {{- include "basestation.labels" . | nindent 4 }}
spec:
  type: {{ .svc.serviceType | default "ClusterIP" }}
  selector:
    {{- include "basestation.selectorLabels" . | nindent 4 }}
  ports:
    - port: {{ .svc.port }}
      targetPort: {{ .svc.port }}
      protocol: TCP
      {{- if .svc.portName }}
      name: {{ .svc.portName }}
      {{- else if .svc.additionalPorts }}
      name: primary
      {{- end }}
    {{- range .svc.additionalPorts }}
    - port: {{ .port }}
      targetPort: {{ .port }}
      protocol: TCP
      name: {{ .name }}
    {{- end }}
{{- end }}
{{- end }}
