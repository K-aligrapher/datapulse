import React, { useEffect, useRef } from 'react';
import * as echarts from 'echarts';

export default function ObservabilityCharts({ selectedSource, metricsData }) {
  const lineChartRef = useRef(null);
  const barChartRef = useRef(null);

  // Filter metrics for the selected source, sorted chronologically
  const filteredMetrics = [...metricsData]
    .filter(m => m.sourceId === selectedSource)
    .sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));

  useEffect(() => {
    let lineChart = null;
    let barChart = null;

    if (lineChartRef.current) {
      lineChart = echarts.init(lineChartRef.current, 'dark', { backgroundColor: 'transparent' });
      
      const timestamps = filteredMetrics.map(m => {
        const d = new Date(m.timestamp);
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
      });
      const scores = filteredMetrics.map(m => Math.round(m.qualityScore * 10) / 10);

      const option = {
        title: {
          text: 'Quality Score Trend (%)',
          left: 'left',
          textStyle: { color: '#ffffff', fontSize: 14, fontFamily: 'Outfit' }
        },
        tooltip: { trigger: 'axis' },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: timestamps.length > 0 ? timestamps : ['No Data'],
          axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } },
          axisLabel: { color: '#9ca3af', fontFamily: 'Outfit' }
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 100,
          splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.05)' } },
          axisLabel: { color: '#9ca3af', formatter: '{value}%', fontFamily: 'Outfit' }
        },
        series: [
          {
            name: 'Score',
            type: 'line',
            smooth: true,
            data: scores.length > 0 ? scores : [100],
            lineStyle: { width: 3, color: '#00d2ff' },
            itemStyle: { color: '#00d2ff' },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(0, 210, 255, 0.3)' },
                { offset: 1, color: 'rgba(0, 210, 255, 0)' }
              ])
            }
          }
        ]
      };
      lineChart.setOption(option);
    }

    if (barChartRef.current && filteredMetrics.length > 0) {
      barChart = echarts.init(barChartRef.current, 'dark', { backgroundColor: 'transparent' });

      // Grab the latest metric entry for issue details
      const latest = filteredMetrics[filteredMetrics.length - 1];

      const option = {
        title: {
          text: 'Quality Issue Breakdown (Latest Batch)',
          left: 'left',
          textStyle: { color: '#ffffff', fontSize: 14, fontFamily: 'Outfit' }
        },
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: {
          type: 'category',
          data: ['Missing Values %', 'Duplicates %', 'Invalid Format %'],
          axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } },
          axisLabel: { color: '#9ca3af', fontFamily: 'Outfit' }
        },
        yAxis: {
          type: 'value',
          splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.05)' } },
          axisLabel: { color: '#9ca3af', formatter: '{value}%', fontFamily: 'Outfit' }
        },
        series: [
          {
            name: 'Percentage',
            type: 'bar',
            barWidth: '40%',
            data: [
              { value: Math.round(latest.missingPercentage * 10) / 10, itemStyle: { color: '#f59e0b' } },
              { value: Math.round(latest.duplicatePercentage * 10) / 10, itemStyle: { color: '#ef4444' } },
              { value: Math.round(latest.invalidPercentage * 10) / 10, itemStyle: { color: '#3b82f6' } }
            ],
            label: {
              show: true,
              position: 'top',
              formatter: '{c}%',
              color: '#ffffff',
              fontFamily: 'Outfit'
            }
          }
        ]
      };
      barChart.setOption(option);
    }

    const handleResize = () => {
      if (lineChart) lineChart.resize();
      if (barChart) barChart.resize();
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      if (lineChart) lineChart.dispose();
      if (barChart) barChart.dispose();
    };
  }, [filteredMetrics]);

  return (
    <div style={styles.chartContainer}>
      <div className="glass-panel" style={styles.chartBox}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--color-primary)', fontWeight: '600' }}>
            Pipeline: {selectedSource}
          </span>
          {filteredMetrics.length === 0 && (
            <span style={{ fontSize: '0.75rem', color: '#6b7280' }}>Waiting for simulator events...</span>
          )}
        </div>
        <div ref={lineChartRef} style={{ width: '100%', height: '280px' }} />
      </div>

      <div className="glass-panel" style={styles.chartBox}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--color-primary)', fontWeight: '600' }}>
            Anomalies and Errors
          </span>
        </div>
        {filteredMetrics.length > 0 ? (
          <div ref={barChartRef} style={{ width: '100%', height: '280px' }} />
        ) : (
          <div style={styles.noDataWrapper}>
            <p style={{ color: '#6b7280', fontSize: '0.9rem' }}>
              No batch runs recorded. Please activate the data simulator to observe issues.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

const styles = {
  chartContainer: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(450px, 1fr))',
    gap: '20px',
    marginBottom: '24px',
  },
  chartBox: {
    padding: '24px',
    borderRadius: '16px',
    height: '350px',
  },
  noDataWrapper: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '280px',
    textAlign: 'center',
  }
};
