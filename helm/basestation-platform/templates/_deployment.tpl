{{/*
Generic deployment template.
Expects dict with keys: root (global context), name (string), svc (service values)
*/}}
{{- define "basestation.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .name }}
  namespace: {{ .root.Release.Namespace }}
  labels:
    {{- include "basestation.labels" . | nindent 4 }}
    {{- with .svc.tier }}
    tier: {{ . }}
    {{- end }}
    {{- with .svc.component }}
    component: {{ . }}
    {{- end }}
spec:
  replicas: {{ .svc.replicas | default 1 }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      {{- include "basestation.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "basestation.selectorLabels" . | nindent 8 }}
        {{- with .svc.tier }}
        tier: {{ . }}
        {{- end }}
        {{- with .svc.component }}
        component: {{ . }}
        {{- end }}
    spec:
      automountServiceAccountToken: false
      terminationGracePeriodSeconds: 30
      {{- with .svc.serviceAccountName }}
      serviceAccountName: {{ . }}
      {{- end }}
      containers:
        - name: {{ .name }}
          image: {{ .image | default (include "basestation.image" .) }}
          imagePullPolicy: {{ .root.Values.global.imagePullPolicy }}
          {{- with .svc.args }}
          args:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          {{- with .svc.command }}
          command:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          {{- if .svc.port }}
          ports:
            - containerPort: {{ .svc.port }}
              protocol: TCP
            {{- range .svc.additionalPorts }}
            - containerPort: {{ .port }}
              name: {{ .name }}
              protocol: TCP
            {{- end }}
          {{- end }}
          {{- if or .svc.env .svc.envFromSecrets }}
          env:
            {{- range $key, $val := .svc.env }}
            - name: {{ $key }}
              value: {{ $val | quote }}
            {{- end }}
            {{- range $key, $ref := .svc.envFromSecrets }}
            - name: {{ $key }}
              valueFrom:
                secretKeyRef:
                  name: {{ $ref.secret }}
                  key: {{ $ref.key }}
            {{- end }}
          {{- end }}
          resources:
            {{- include "basestation.resources" . | nindent 12 }}
          securityContext:
            {{- include "basestation.securityContext" . | nindent 12 }}
          {{- if .svc.probes }}
          {{- with .svc.probes.liveness }}
          livenessProbe:
            {{- if eq (.type | default "http") "http" }}
            httpGet:
              path: {{ .path }}
              port: {{ $.svc.port }}
            {{- else if eq .type "tcp" }}
            tcpSocket:
              port: {{ $.svc.port }}
            {{- else if eq .type "exec" }}
            exec:
              command:
                {{- toYaml .command | nindent 16 }}
            {{- end }}
            initialDelaySeconds: {{ .initialDelay | default 30 }}
            periodSeconds: {{ .period | default 10 }}
            timeoutSeconds: {{ .timeout | default 3 }}
            failureThreshold: {{ .failureThreshold | default 3 }}
            successThreshold: {{ .successThreshold | default 1 }}
          {{- end }}
          {{- with .svc.probes.readiness }}
          readinessProbe:
            {{- if eq (.type | default "http") "http" }}
            httpGet:
              path: {{ .path | default $.svc.probes.liveness.path }}
              port: {{ $.svc.port }}
            {{- else if eq .type "tcp" }}
            tcpSocket:
              port: {{ $.svc.port }}
            {{- else if eq .type "exec" }}
            exec:
              command:
                {{- toYaml .command | nindent 16 }}
            {{- end }}
            initialDelaySeconds: {{ .initialDelay | default 10 }}
            periodSeconds: {{ .period | default 5 }}
            timeoutSeconds: {{ .timeout | default 3 }}
            failureThreshold: {{ .failureThreshold | default 3 }}
            successThreshold: {{ .successThreshold | default 1 }}
          {{- end }}
          {{- end }}
          {{- if .svc.volumes }}
          volumeMounts:
            {{- range .svc.volumes }}
            - name: {{ .name }}
              mountPath: {{ .mountPath }}
              {{- if .readOnly }}
              readOnly: {{ .readOnly }}
              {{- end }}
            {{- end }}
          {{- end }}
      {{- if .svc.volumes }}
      volumes:
        {{- range .svc.volumes }}
        - name: {{ .name }}
          {{- if eq .type "emptyDir" }}
          emptyDir: {}
          {{- else if eq .type "pvc" }}
          persistentVolumeClaim:
            claimName: {{ .claimName }}
          {{- else if eq .type "configMap" }}
          configMap:
            name: {{ .configMapName }}
          {{- else if eq .type "hostPath" }}
          hostPath:
            path: {{ .hostPath }}
          {{- end }}
        {{- end }}
      {{- end }}
{{- end }}
