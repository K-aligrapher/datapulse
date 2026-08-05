import React, { useState, useEffect } from 'react';
import Login from './components/Login';
import DashboardOverview from './components/DashboardOverview';
import ObservabilityCharts from './components/ObservabilityCharts';
import IngestionSimulator from './components/IngestionSimulator';
import AlertsList from './components/AlertsList';
import RulesManager from './components/RulesManager';

export default function App() {
  const [token, setToken] = useState(localStorage.getItem('datapulse_token') || null);
  const [username, setUsername] = useState(localStorage.getItem('datapulse_username') || null);
  const [selectedSource, setSelectedSource] = useState('sales-data');
  const [overview, setOverview] = useState({ totalJobs: 0, activeAlerts: 0, averageQualityScore: 100, sources: [] });
  const [rules, setRules] = useState([]);
  const [metrics, setMetrics] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [throughputs, setThroughputs] = useState({});

  const handleLoginSuccess = (userToken, userName) => {
    setToken(userToken);
    setUsername(userName);
  };

  const handleLogout = () => {
    localStorage.removeItem('datapulse_token');
    localStorage.removeItem('datapulse_username');
    setToken(null);
    setUsername(null);
  };

  // 1. Initial REST API Data Pulls
  const fetchDashboardData = async () => {
    if (!token) return;
    try {
      const headers = { 'Authorization': `Bearer ${token}` };

      // Pull overview, rules, alerts, and metrics
      const [resOverview, resRules, resAlerts, resMetrics] = await Promise.all([
        fetch('http://localhost:8080/api/dashboard/overview', { headers }),
        fetch('http://localhost:8080/api/rules', { headers }),
        fetch('http://localhost:8080/api/alerts', { headers }),
        fetch('http://localhost:8080/api/metrics', { headers })
      ]);

      if (resOverview.status === 401 || resRules.status === 401) {
        handleLogout();
        return;
      }

      const dataOverview = await resOverview.json();
      const dataRules = await resRules.json();
      const dataAlerts = await resAlerts.json();
      const dataMetrics = await resMetrics.json();

      setOverview(dataOverview);
      setRules(dataRules);
      setAlerts(dataAlerts);
      setMetrics(dataMetrics);
    } catch (err) {
      console.error('Failed to sync dashboard REST APIs', err);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, [token]);

  // 2. Establish Real-Time WebSocket Channel
  useEffect(() => {
    if (!token) return;

    let socket;
    const connectWebSocket = () => {
      socket = new WebSocket('ws://localhost:8080/ws/metrics');

      socket.onopen = () => {
        console.log('Real-Time DataPulse WebSocket Stream open');
      };

      socket.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          
          if (msg.type === 'THROUGHPUT') {
            setThroughputs(prev => ({
              ...prev,
              [msg.topic]: { rate: msg.rate, batchSize: msg.batchSize }
            }));
          } else if (msg.type === 'METRIC') {
            // Append to metrics array for charts
            setMetrics(prev => [
              ...prev,
              {
                sourceId: msg.sourceId,
                missingPercentage: msg.missingPercentage,
                duplicatePercentage: msg.duplicatePercentage,
                invalidPercentage: msg.invalidPercentage,
                schemaChanges: msg.schemaChanges,
                outlierCount: msg.outlierCount,
                qualityScore: msg.qualityScore,
                timestamp: msg.timestamp
              }
            ]);

            // Dynamically update the qualityScore and status of the source in local state overview
            setOverview(prev => {
              const updatedSources = prev.sources.map(s => {
                if (s.sourceId === msg.sourceId) {
                  let status = 'GREEN';
                  if (msg.qualityScore < 80.0) status = 'RED';
                  else if (msg.qualityScore < 95.0) status = 'YELLOW';
                  return { ...s, qualityScore: msg.qualityScore, status };
                }
                return s;
              });

              // Recompute aggregate score
              const sum = updatedSources.reduce((acc, c) => acc + c.qualityScore, 0);
              const avg = updatedSources.length > 0 ? sum / updatedSources.length : 100;

              return {
                ...prev,
                averageQualityScore: Math.round(avg * 10) / 10,
                sources: updatedSources
              };
            });
          } else if (msg.type === 'ALERT') {
            setAlerts(prev => [
              {
                alertId: msg.alertId,
                sourceId: msg.sourceId,
                severity: msg.severity,
                message: msg.message,
                createdAt: msg.createdAt,
                resolved: msg.resolved
              },
              ...prev
            ]);
            setOverview(prev => ({
              ...prev,
              activeAlerts: prev.activeAlerts + 1
            }));
          }
        } catch (err) {
          console.error('Failed to parse socket payload', err);
        }
      };

      socket.onclose = () => {
        console.log('Socket disconnected. Retrying in 5 seconds...');
        setTimeout(connectWebSocket, 5000);
      };

      socket.onerror = (err) => {
        console.error('WebSocket Error:', err);
      };
    };

    connectWebSocket();

    return () => {
      if (socket) socket.close();
    };
  }, [token]);

  // Handler toggles inside children
  const handleRuleChange = (updatedRule) => {
    setRules(prev => prev.map(r => r.ruleId === updatedRule.ruleId ? updatedRule : r));
  };

  const handleResolveAlert = (alertId) => {
    setAlerts(prev => prev.filter(a => a.alertId !== alertId));
    setOverview(prev => ({
      ...prev,
      activeAlerts: Math.max(0, prev.activeAlerts - 1)
    }));
  };

  const handleSimulatorToggle = async () => {
    try {
      const isRunning = overview.simulatorRunning;
      const url = `http://localhost:8080/api/dashboard/simulator/${isRunning ? 'stop' : 'start'}`;
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (response.ok) {
        setOverview(prev => ({ ...prev, simulatorRunning: !isRunning }));
      }
    } catch (err) {
      console.error(err);
    }
  };

  const handleAnomalyToggle = async () => {
    try {
      const induce = !overview.induceAnomalies;
      const response = await fetch('http://localhost:8080/api/dashboard/simulator/anomalies', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ induceAnomalies: induce })
      });
      if (response.ok) {
        setOverview(prev => ({ ...prev, induceAnomalies: induce }));
      }
    } catch (err) {
      console.error(err);
    }
  };

  if (!token) {
    return <Login onLoginSuccess={handleLoginSuccess} />;
  }

  return (
    <div style={styles.dashboardLayout}>
      {/* Header bar */}
      <header className="glass-panel" style={styles.header}>
        <div style={styles.logoGroup}>
          <span style={styles.logoIcon}>⚡</span>
          <span style={styles.logoText}>DATAPULSE</span>
        </div>

        <div style={styles.userControls}>
          <div style={styles.userBadge}>
            <span style={styles.userDot}>●</span>
            <span style={{ fontSize: '0.9rem', color: '#e5e7eb' }}>{username} (Admin)</span>
          </div>
          <button onClick={handleLogout} className="glow-btn-secondary" style={{ padding: '8px 16px', fontSize: '0.85rem' }}>
            Sign Out
          </button>
        </div>
      </header>

      {/* Main Body view */}
      <main style={styles.main}>
        <DashboardOverview 
          overview={overview} 
          throughputs={throughputs}
          selectedSource={selectedSource}
          onSelectSource={setSelectedSource}
        />

        <ObservabilityCharts 
          selectedSource={selectedSource}
          metricsData={metrics}
        />

        <div style={styles.flexRow}>
          <IngestionSimulator
            overview={overview}
            onSimulatorToggle={handleSimulatorToggle}
            onAnomalyToggle={handleAnomalyToggle}
          />
          <AlertsList
            alerts={alerts}
            onResolveAlert={handleResolveAlert}
          />
        </div>

        <RulesManager
          selectedSource={selectedSource}
          rules={rules}
          onRuleChange={handleRuleChange}
        />
      </main>
    </div>
  );
}

const styles = {
  dashboardLayout: {
    minHeight: '100vh',
    display: 'flex',
    flexDirection: 'column',
    background: 'radial-gradient(circle at top right, #0e1628 0%, #030712 100%)',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '16px 32px',
    margin: '20px',
    borderRadius: '16px',
  },
  logoGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  logoIcon: {
    fontSize: '1.5rem',
  },
  logoText: {
    fontSize: '1.25rem',
    fontWeight: '800',
    letterSpacing: '0.08em',
    background: 'linear-gradient(to right, #00d2ff, #0088ff)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
  },
  userControls: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  },
  userBadge: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '6px 12px',
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderRadius: '20px',
    border: '1px solid rgba(255, 255, 255, 0.05)',
  },
  userDot: {
    color: '#10b981',
    fontSize: '0.75rem',
  },
  main: {
    flex: 1,
    padding: '0 20px 40px 20px',
    maxWidth: '1440px',
    width: '100%',
    margin: '0 auto',
  },
  flexRow: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '20px',
    marginBottom: '24px',
  }
};
