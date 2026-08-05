import React from 'react';

export default function AlertsList({ alerts, onResolveAlert }) {
  const getSeverityStyle = (sev) => {
    if (sev === 'CRITICAL') {
      return { bg: 'rgba(239, 68, 68, 0.15)', text: '#ef4444', border: 'rgba(239, 68, 68, 0.2)' };
    }
    if (sev === 'ERROR') {
      return { bg: 'rgba(245, 158, 11, 0.15)', text: '#f59e0b', border: 'rgba(245, 158, 11, 0.2)' };
    }
    return { bg: 'rgba(59, 130, 246, 0.15)', text: '#3b82f6', border: 'rgba(59, 130, 246, 0.2)' };
  };

  const handleResolve = async (alertId) => {
    try {
      const token = localStorage.getItem('datapulse_token');
      const response = await fetch(`http://localhost:8080/api/alerts/${alertId}/resolve`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.ok) {
        onResolveAlert(alertId);
      } else {
        alert('Failed to resolve alert');
      }
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  return (
    <div className="glass-panel" style={{ padding: '24px', flex: '1 1 350px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h2 style={{ fontSize: '1.25rem', fontWeight: '600', letterSpacing: '0.02em' }}>
          Active System Alerts
        </h2>
        <span style={{
          fontSize: '0.75rem',
          fontWeight: '700',
          backgroundColor: alerts.length > 0 ? 'rgba(239, 68, 68, 0.2)' : 'rgba(16, 185, 129, 0.2)',
          color: alerts.length > 0 ? '#f87171' : '#34d399',
          padding: '4px 10px',
          borderRadius: '20px'
        }}>
          {alerts.length} Pending
        </span>
      </div>

      {alerts.length === 0 ? (
        <div style={styles.noAlerts}>
          <span style={{ fontSize: '2.5rem', marginBottom: '8px' }}>🎉</span>
          <p style={{ color: '#10b981', fontWeight: '600', fontSize: '0.95rem' }}>All pipelines normal</p>
          <p style={{ color: '#6b7280', fontSize: '0.75rem' }}>No data quality rules are currently failing</p>
        </div>
      ) : (
        <div style={styles.list}>
          {alerts.map((alert) => {
            const colors = getSeverityStyle(alert.severity);
            const time = new Date(alert.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            return (
              <div
                key={alert.alertId}
                style={{
                  ...styles.alertCard,
                  backgroundColor: colors.bg,
                  borderColor: colors.border,
                  borderLeft: `4px solid ${colors.text}`
                }}
              >
                <div style={styles.cardHeader}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ ...styles.badge, color: colors.text, border: `1px solid ${colors.text}` }}>
                      {alert.severity}
                    </span>
                    <code style={{ fontSize: '0.8rem', color: '#ffffff', fontWeight: '600' }}>{alert.sourceId}</code>
                  </div>
                  <span style={{ fontSize: '0.75rem', color: '#9ca3af' }}>{time}</span>
                </div>
                
                <p style={styles.message}>{alert.message}</p>
                
                <button
                  onClick={() => handleResolve(alert.alertId)}
                  className="glow-btn"
                  style={styles.resolveBtn}
                >
                  Resolve Issue
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

const styles = {
  noAlerts: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '40px 0',
    textAlign: 'center',
  },
  list: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
    maxHeight: '400px',
    overflowY: 'auto',
    paddingRight: '4px',
  },
  alertCard: {
    border: '1px solid',
    borderRadius: '8px',
    padding: '16px',
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
  },
  cardHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  badge: {
    fontSize: '0.65rem',
    fontWeight: '800',
    padding: '2px 6px',
    borderRadius: '4px',
    letterSpacing: '0.05em',
  },
  message: {
    fontSize: '0.85rem',
    color: '#f3f4f6',
    lineHeight: '1.4',
  },
  resolveBtn: {
    alignSelf: 'flex-end',
    padding: '6px 12px',
    fontSize: '0.75rem',
    fontWeight: '600',
    borderRadius: '4px',
  }
};
