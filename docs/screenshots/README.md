# Screenshots

Add screenshots of the running application here.

## Recommended Screenshots

1. **dashboard.png** - Main dashboard with station health overview
2. **metrics.png** - Metrics page with time-series charts
3. **stations.png** - Station management table with CRUD actions

## How to Capture

1. Deploy to minikube: `helm install basestation helm/basestation-platform -n basestation-platform`
2. Wait for all pods to be Running: `kubectl get pods -n basestation-platform`
3. Open `http://basestation.local:{ingress-port}` in your browser
4. Take screenshots of each page

## Note

Screenshots can be added here when available. The main README does not currently reference a screenshots section.
