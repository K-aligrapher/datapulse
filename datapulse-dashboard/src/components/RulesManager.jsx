import React, { useState } from 'react';

export default function RulesManager({ selectedSource, rules, onRuleChange }) {
  const [editingRuleId, setEditingRuleId] = useState(null);
  const [editThreshold, setEditThreshold] = useState('');
  const [editSeverity, setEditSeverity] = useState('WARNING');
  const [loading, setLoading] = useState(false);

  const filteredRules = rules.filter(r => r.sourceId === selectedSource);

  const startEdit = (rule) => {
    setEditingRuleId(rule.ruleId);
    setEditThreshold(rule.threshold !== null ? rule.threshold.toString() : '');
    setEditSeverity(rule.severity);
  };

  const cancelEdit = () => {
    setEditingRuleId(null);
  };

  const handleSave = async (ruleId, rule) => {
    setLoading(true);
    try {
      const token = localStorage.getItem('datapulse_token');
      const response = await fetch(`http://localhost:8080/api/rules/${ruleId}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          ...rule,
          threshold: editThreshold === '' ? null : parseFloat(editThreshold),
          severity: editSeverity
        })
      });

      if (response.ok) {
        const updated = await response.json();
        onRuleChange(updated);
        setEditingRuleId(null);
      } else {
        alert('Failed to update rule');
      }
    } catch (err) {
      alert('Error updating rule: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = async (ruleId) => {
    try {
      const token = localStorage.getItem('datapulse_token');
      const response = await fetch(`http://localhost:8080/api/rules/${ruleId}/toggle`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.ok) {
        const updated = await response.json();
        onRuleChange(updated);
      } else {
        alert('Failed to toggle rule');
      }
    } catch (err) {
      alert('Error toggling rule: ' + err.message);
    }
  };

  return (
    <div className="glass-panel" style={{ padding: '24px', marginBottom: '24px' }}>
      <h2 style={{ fontSize: '1.25rem', fontWeight: '600', marginBottom: '16px', letterSpacing: '0.02em' }}>
        Rule Engine Configuration: <span style={{ color: 'var(--color-primary)' }}>{selectedSource}</span>
      </h2>

      {filteredRules.length === 0 ? (
        <p style={{ color: '#6b7280', fontSize: '0.9rem', textAlign: 'center', padding: '20px' }}>
          No validation rules configured for this pipeline.
        </p>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={styles.table}>
            <thead>
              <tr style={styles.headerRow}>
                <th style={styles.th}>Active</th>
                <th style={styles.th}>Rule Name</th>
                <th style={styles.th}>Check Type</th>
                <th style={styles.th}>Field</th>
                <th style={styles.th}>Threshold Value</th>
                <th style={styles.th}>Severity Level</th>
                <th style={styles.th} style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredRules.map((rule) => {
                const isEditing = editingRuleId === rule.ruleId;
                return (
                  <tr key={rule.ruleId} style={styles.row}>
                    <td style={styles.td}>
                      <label style={styles.switch}>
                        <input
                          type="checkbox"
                          checked={rule.enabled}
                          onChange={() => handleToggle(rule.ruleId)}
                          style={{ display: 'none' }}
                        />
                        <span style={{
                          ...styles.slider,
                          backgroundColor: rule.enabled ? 'var(--color-success)' : 'rgba(255,255,255,0.1)'
                        }} />
                      </label>
                    </td>
                    <td style={{ ...styles.td, fontWeight: '500', color: '#ffffff' }}>
                      {rule.ruleName}
                    </td>
                    <td style={styles.td}>
                      <code style={styles.code}>{rule.ruleType}</code>
                    </td>
                    <td style={styles.td}>{rule.fieldName}</td>
                    <td style={styles.td}>
                      {isEditing ? (
                        <input
                          type="number"
                          value={editThreshold}
                          onChange={(e) => setEditThreshold(e.target.value)}
                          style={styles.editInput}
                          placeholder="None"
                          step="any"
                        />
                      ) : (
                        rule.threshold !== null ? rule.threshold : <span style={{ color: '#4b5563' }}>N/A</span>
                      )}
                    </td>
                    <td style={styles.td}>
                      {isEditing ? (
                        <select
                          value={editSeverity}
                          onChange={(e) => setEditSeverity(e.target.value)}
                          style={styles.select}
                        >
                          <option value="WARNING">WARNING</option>
                          <option value="ERROR">ERROR</option>
                          <option value="CRITICAL">CRITICAL</option>
                        </select>
                      ) : (
                        <span style={{
                          fontSize: '0.75rem',
                          fontWeight: '700',
                          padding: '4px 8px',
                          borderRadius: '4px',
                          backgroundColor: rule.severity === 'CRITICAL' ? 'rgba(239, 68, 68, 0.15)' : rule.severity === 'ERROR' ? 'rgba(245, 158, 11, 0.15)' : 'rgba(59, 130, 246, 0.15)',
                          color: rule.severity === 'CRITICAL' ? '#f87171' : rule.severity === 'ERROR' ? '#fbbf24' : '#60a5fa'
                        }}>
                          {rule.severity}
                        </span>
                      )}
                    </td>
                    <td style={{ ...styles.td, textAlign: 'right' }}>
                      {isEditing ? (
                        <div style={styles.btnGroup}>
                          <button
                            onClick={() => handleSave(rule.ruleId, rule)}
                            className="glow-btn"
                            style={{ padding: '6px 12px', fontSize: '0.8rem' }}
                            disabled={loading}
                          >
                            Save
                          </button>
                          <button
                            onClick={cancelEdit}
                            className="glow-btn-secondary"
                            style={{ padding: '6px 12px', fontSize: '0.8rem', borderColor: '#4b5563', color: '#9ca3af' }}
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <button
                          onClick={() => startEdit(rule)}
                          className="glow-btn-secondary"
                          style={{ padding: '6px 12px', fontSize: '0.8rem' }}
                        >
                          Edit
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

const styles = {
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    textAlign: 'left',
  },
  headerRow: {
    borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
  },
  th: {
    padding: '12px 16px',
    color: '#6b7280',
    fontSize: '0.75rem',
    textTransform: 'uppercase',
    fontWeight: '600',
    letterSpacing: '0.05em',
  },
  row: {
    borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
  },
  td: {
    padding: '16px',
    fontSize: '0.9rem',
    color: '#d1d5db',
    verticalAlign: 'middle',
  },
  code: {
    color: '#00d2ff',
    fontFamily: 'monospace',
    backgroundColor: 'rgba(0, 210, 255, 0.05)',
    padding: '2px 6px',
    borderRadius: '4px',
    fontSize: '0.8rem',
  },
  switch: {
    position: 'relative',
    display: 'inline-block',
    width: '36px',
    height: '20px',
    cursor: 'pointer',
  },
  slider: {
    position: 'absolute',
    cursor: 'pointer',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    borderRadius: '20px',
    transition: '0.3s',
  },
  editInput: {
    width: '80px',
    padding: '6px 8px',
    background: 'rgba(0, 0, 0, 0.2)',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '4px',
    color: '#ffffff',
    fontSize: '0.85rem',
    outline: 'none',
  },
  select: {
    padding: '6px 8px',
    background: 'rgba(0, 0, 0, 0.2)',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '4px',
    color: '#ffffff',
    fontSize: '0.85rem',
    outline: 'none',
  },
  btnGroup: {
    display: 'flex',
    gap: '8px',
    justifyContent: 'flex-end',
  }
};
