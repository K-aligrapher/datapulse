import React from 'react';

export default function DashboardOverview({ overview, throughputs, onSelectSource, selectedSource }) {
  const getStatusColorClass = (status) => {
    if (status === 'GREEN') return 'success';
    if (status === 'YELLOW') return 'warning';
    return 'danger';
  };

  const getStatusIndicator = (status) => {
    if (status === 'GREEN') return <span className="pulse-indicator" />;
    if (status === 'YELLOW') return <span className="pulse-indicator amber" />;
    return <span className="pulse-indicator red" />;
  };

  // Sum up all rates from all active throughput brokers
  const totalThroughput = Object.values(throughputs).reduce((acc, curr) => acc + curr.rate, 0);

  return (
    <div>
      <div className="dashboard-grid">
        {/* Card 1: Overall Quality Score */}
        <div className={`glass-panel card-stat ${overview.averageQualityScore >= 95 ? 'success' : overview.averageQualityScore >= 80 ? 'warning' : 'danger'}`}>
          <span className="stat-label">Average Quality Score</span>
          <span className="stat-val">{overview.averageQualityScore ?? '100'}%</span>
          <span className="stat-desc">Weighted average score of active pipelines</span>
        </div>

        {/* Card 2: Kafka Throughput */}
        <div className="glass-panel card-stat">
          <span className="stat-label">Kafka System Flow</span>
          <span className="stat-val">{totalThroughput} rec/s</span>
          <span className="stat-desc">Aggregated incoming Kafka throughput</span>
        </div>

        {/* Card 3: Failed Records */}
        <div className={`glass-panel card-stat ${overview.activeAlerts > 0 ? 'danger' : 'success'}`}>
          <span className="stat-label">Active Alerts</span>
          <span className="stat-val" style={{ color: overview.activeAlerts > 0 ? '#ef4444' : '#10b981' }}>
            {overview.activeAlerts}
          </span>
          <span className="stat-desc">Unresolved rule engine validation breaches</span>
        </div>

        {/* Card 4: Jobs Processed */}
        <div className="glass-panel card-stat">
          <span className="stat-label">Total Ingestion Jobs</span>
          <span className="stat-val">{overview.totalJobs}</span>
          <span className="stat-desc">Total executed import jobs in metadata DB</span>
        </div>
      </div>

      {/* Dataset Health Grid Section */}
      <div className="glass-panel" style={{ padding: '24px', marginBottom: '24px' }}>
        <h2 style={{ fontSize: '1.25rem', fontWeight: '600', marginBottom: '16px', letterSpacing: '0.02em' }}>
          Dataset Quality Health Map
        </h2>
        <div style={{ overflowX: 'auto' }}>
          <table style={tableStyles.table}>
            <thead>
              <tr style={tableStyles.headerRow}>
                <th style={tableStyles.th}>Status</th>
                <th style={tableStyles.th}>Data Source</th>
                <th style={tableStyles.th}>Pipeline ID</th>
                <th style={tableStyles.th}>Metric Type</th>
                <th style={tableStyles.th}>Score</th>
                <th style={tableStyles.th}>Ingestion Rate</th>
                <th style={tableStyles.th}>Owner</th>
              </tr>
            </thead>
            <tbody>
              {overview.sources && overview.sources.map((src) => {
                const rate = throughputs[src.sourceId]?.rate ?? 0;
                const isSelected = selectedSource === src.sourceId;
                return (
                  <tr 
                    key={src.sourceId} 
                    onClick={() => onSelectSource(src.sourceId)}
                    style={{
                      ...tableStyles.row,
                      backgroundColor: isSelected ? 'rgba(0, 210, 255, 0.08)' : 'transparent',
                      borderLeft: isSelected ? '3px solid var(--color-primary)' : '3px solid transparent'
                    }}
                  >
                    <td style={tableStyles.td}>
                      <div style={{ display: 'flex', alignItems: 'center' }}>
                        {getStatusIndicator(src.status)}
                        <span style={{ fontSize: '0.75rem', fontWeight: '600', color: src.status === 'GREEN' ? '#10b981' : src.status === 'YELLOW' ? '#f59e0b' : '#ef4444' }}>
                          {src.status}
                        </span>
                      </div>
                    </td>
                    <td style={{ ...tableStyles.td, fontWeight: '600', color: '#ffffff' }}>{src.name}</td>
                    <td style={tableStyles.td}><code style={{ color: '#00d2ff', fontSize: '0.8rem' }}>{src.sourceId}</code></td>
                    <td style={tableStyles.td}>{src.type}</td>
                    <td style={{ ...tableStyles.td, fontWeight: '700', color: src.qualityScore >= 95 ? '#10b981' : src.qualityScore >= 80 ? '#f59e0b' : '#ef4444' }}>
                      {Math.round(src.qualityScore * 10) / 10}%
                    </td>
                    <td style={tableStyles.td}>{rate} rec/s</td>
                    <td style={tableStyles.td}><span style={{ color: '#9ca3af', fontSize: '0.85rem' }}>{src.owner}</span></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

const tableStyles = {
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
    cursor: 'pointer',
    transition: 'background-color 0.2s',
  },
  td: {
    padding: '16px',
    fontSize: '0.9rem',
    color: '#d1d5db',
  }
};
