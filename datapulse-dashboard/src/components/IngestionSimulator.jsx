import React, { useState } from 'react';

export default function IngestionSimulator({ overview, onSimulatorToggle, onAnomalyToggle }) {
  const [targetTopic, setTargetTopic] = useState('sales-data');
  const [jsonText, setJsonText] = useState('');
  const [csvText, setCsvText] = useState('');
  const [activeTab, setActiveTab] = useState('simulator'); // 'simulator', 'json', 'csv'
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState({ message: '', isError: false });

  const showFeedback = (msg, isErr = false) => {
    setFeedback({ message: msg, isError: isErr });
    setTimeout(() => setFeedback({ message: '', isError: false }), 5000);
  };

  const handleJsonSubmit = async () => {
    if (!jsonText.trim()) return;
    setSubmitting(true);
    try {
      let isArray = jsonText.trim().startsWith('[');
      const url = `http://localhost:8080/api/ingest/${isArray ? 'json' : 'event'}?topic=${targetTopic}`;
      const token = localStorage.getItem('datapulse_token');

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: jsonText
      });

      if (response.ok) {
        showFeedback('JSON Ingestion successful! Event triggered.');
        setJsonText('');
      } else {
        const err = await response.json();
        showFeedback(err.error || 'Ingestion failed', true);
      }
    } catch (err) {
      showFeedback('Connection to backend failed: ' + err.message, true);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCsvSubmit = async () => {
    if (!csvText.trim()) return;
    setSubmitting(true);
    try {
      const url = `http://localhost:8080/api/ingest/csv?topic=${targetTopic}`;
      const token = localStorage.getItem('datapulse_token');

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'text/csv',
          'Authorization': `Bearer ${token}`
        },
        body: csvText
      });

      if (response.ok) {
        const data = await response.json();
        showFeedback(`CSV Ingestion successful! Imported ${data.recordsIngested} rows.`);
        setCsvText('');
      } else {
        const err = await response.json();
        showFeedback(err.error || 'CSV Ingestion failed', true);
      }
    } catch (err) {
      showFeedback('Connection to backend failed: ' + err.message, true);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="glass-panel" style={{ padding: '24px', marginBottom: '24px' }}>
      <div style={styles.tabHeaders}>
        <button
          onClick={() => setActiveTab('simulator')}
          style={{ ...styles.tabBtn, borderBottom: activeTab === 'simulator' ? '2px solid var(--color-primary)' : '2px solid transparent', color: activeTab === 'simulator' ? '#ffffff' : '#6b7280' }}
        >
          ⚡ Live Stream Simulator
        </button>
        <button
          onClick={() => setActiveTab('json')}
          style={{ ...styles.tabBtn, borderBottom: activeTab === 'json' ? '2px solid var(--color-primary)' : '2px solid transparent', color: activeTab === 'json' ? '#ffffff' : '#6b7280' }}
        >
          {'{ }'} Direct JSON Portal
        </button>
        <button
          onClick={() => setActiveTab('csv')}
          style={{ ...styles.tabBtn, borderBottom: activeTab === 'csv' ? '2px solid var(--color-primary)' : '2px solid transparent', color: activeTab === 'csv' ? '#ffffff' : '#6b7280' }}
        >
          📄 CSV Upload Portal
        </button>
      </div>

      {feedback.message && (
        <div style={{
          ...styles.feedback,
          backgroundColor: feedback.isError ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
          color: feedback.isError ? '#ef4444' : '#10b981',
          borderColor: feedback.isError ? 'rgba(239, 68, 68, 0.2)' : 'rgba(16, 185, 129, 0.2)'
        }}>
          {feedback.message}
        </div>
      )}

      {/* Simulator Control View */}
      {activeTab === 'simulator' && (
        <div style={styles.simContainer}>
          <div style={styles.simControls}>
            <button
              onClick={onSimulatorToggle}
              className="glow-btn"
              style={{
                ...styles.actionBtn,
                background: overview.simulatorRunning ? 'linear-gradient(135deg, #ef4444, #dc2626)' : 'linear-gradient(135deg, var(--color-primary), #0088ff)',
                color: overview.simulatorRunning ? '#ffffff' : '#030712'
              }}
            >
              {overview.simulatorRunning ? 'Stop Data Simulator' : 'Start Data Simulator'}
            </button>

            <button
              onClick={onAnomalyToggle}
              className="glow-btn-secondary"
              style={{
                ...styles.actionBtn,
                borderColor: overview.induceAnomalies ? 'var(--color-danger)' : 'var(--color-primary)',
                color: overview.induceAnomalies ? 'var(--color-danger)' : 'var(--color-primary)',
                backgroundColor: overview.induceAnomalies ? 'rgba(239, 68, 68, 0.08)' : 'transparent'
              }}
            >
              {overview.induceAnomalies ? 'Disable Anomalies Mode' : 'Enable Anomalies Mode'}
            </button>
          </div>
          
          <div style={styles.simStatus}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span className={`pulse-indicator ${overview.simulatorRunning ? '' : 'red'}`} />
              <span style={{ fontSize: '0.9rem', color: '#9ca3af' }}>
                Simulator: <strong>{overview.simulatorRunning ? 'RUNNING' : 'STOPPED'}</strong>
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span className={`pulse-indicator ${overview.induceAnomalies ? 'amber' : 'red'}`} />
              <span style={{ fontSize: '0.9rem', color: '#9ca3af' }}>
                Anomalies Generation: <strong>{overview.induceAnomalies ? 'ACTIVE' : 'INACTIVE'}</strong>
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Custom JSON Portal View */}
      {activeTab === 'json' && (
        <div style={styles.formContainer}>
          <div style={styles.inputGroup}>
            <label style={styles.label}>Pipeline Target Topic</label>
            <select value={targetTopic} onChange={(e) => setTargetTopic(e.target.value)} style={styles.select}>
              <option value="sales-data">sales-data (Sales Transactions)</option>
              <option value="customer-data">customer-data (Customer Profiles)</option>
              <option value="sensor-data">sensor-data (IoT Sensors)</option>
              <option value="payment-data">payment-data (Payments)</option>
              <option value="inventory-data">inventory-data (Warehouse Inventory)</option>
            </select>
          </div>

          <div style={styles.inputGroup}>
            <label style={styles.label}>JSON Payload (Single Object or Array)</label>
            <textarea
              value={jsonText}
              onChange={(e) => setJsonText(e.target.value)}
              placeholder='e.g., {"transactionId":"TX-999","customerId":"C-222","productId":"P-10","amount":450.0,"timestamp":"2026-08-05T23:00:00Z"}'
              style={styles.textarea}
            />
          </div>

          <button onClick={handleJsonSubmit} className="glow-btn" disabled={submitting || !jsonText.trim()} style={styles.submitBtn}>
            {submitting ? 'Ingesting Record...' : 'Publish JSON Payload'}
          </button>
        </div>
      )}

      {/* Custom CSV Import View */}
      {activeTab === 'csv' && (
        <div style={styles.formContainer}>
          <div style={styles.inputGroup}>
            <label style={styles.label}>Pipeline Target Topic</label>
            <select value={targetTopic} onChange={(e) => setTargetTopic(e.target.value)} style={styles.select}>
              <option value="sales-data">sales-data (Sales Transactions)</option>
              <option value="customer-data">customer-data (Customer Profiles)</option>
              <option value="sensor-data">sensor-data (IoT Sensors)</option>
              <option value="payment-data">payment-data (Payments)</option>
              <option value="inventory-data">inventory-data (Warehouse Inventory)</option>
            </select>
          </div>

          <div style={styles.inputGroup}>
            <label style={styles.label}>CSV Payload (Include header row)</label>
            <textarea
              value={csvText}
              onChange={(e) => setCsvText(e.target.value)}
              placeholder="transactionId,customerId,productId,amount,timestamp&#10;TX-8801,C-991,P-88,125.50,2026-08-05T23:00:00Z"
              style={styles.textarea}
            />
          </div>

          <button onClick={handleCsvSubmit} className="glow-btn" disabled={submitting || !csvText.trim()} style={styles.submitBtn}>
            {submitting ? 'Processing CSV File...' : 'Publish CSV Dataset'}
          </button>
        </div>
      )}
    </div>
  );
}

const styles = {
  tabHeaders: {
    display: 'flex',
    gap: '20px',
    borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
    marginBottom: '20px',
  },
  tabBtn: {
    background: 'none',
    border: 'none',
    padding: '12px 4px',
    fontSize: '0.95rem',
    fontWeight: '600',
    cursor: 'pointer',
    fontFamily: 'var(--font-sans)',
  },
  simContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: '20px',
  },
  simControls: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '16px',
  },
  actionBtn: {
    flex: '1 1 200px',
    padding: '16px',
    fontSize: '1rem',
  },
  simStatus: {
    display: 'flex',
    gap: '30px',
    marginTop: '4px',
  },
  formContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  inputGroup: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
  },
  label: {
    fontSize: '0.85rem',
    color: '#9ca3af',
    fontWeight: '500',
  },
  select: {
    padding: '10px 14px',
    background: 'rgba(0, 0, 0, 0.2)',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '8px',
    color: '#ffffff',
    fontSize: '0.9rem',
    outline: 'none',
  },
  textarea: {
    height: '120px',
    padding: '12px',
    background: 'rgba(0, 0, 0, 0.2)',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '8px',
    color: '#ffffff',
    fontSize: '0.9rem',
    fontFamily: 'monospace',
    outline: 'none',
    resize: 'vertical',
  },
  submitBtn: {
    alignSelf: 'flex-start',
    padding: '12px 24px',
  },
  feedback: {
    padding: '12px',
    borderRadius: '8px',
    fontSize: '0.875rem',
    border: '1px solid',
    marginBottom: '16px',
    textAlign: 'center',
  }
};
