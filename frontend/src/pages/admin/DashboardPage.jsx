import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Bar, BarChart, CartesianGrid, Cell, LabelList, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import api from '../../api/client';
import useLoad from '../../lib/useLoad';
import { ErrorBox, Loading, PageHeader } from '../../components/ui';

// Chart colours come from CSS tokens (see index.css), so light and dark mode each get their own validated step.
const SERIES = 'var(--series-1)';
const SLA_META = {
  ON_TRACK: { text: 'On track', icon: '✓', color: 'var(--status-good)' },
  AT_RISK: { text: 'At risk', icon: '!', color: 'var(--status-warning)' },
  VIOLATED: { text: 'Violated', icon: '✕', color: 'var(--status-critical)' },
};
const AXIS = { stroke: 'var(--border)', tick: { fill: 'var(--text-secondary)', fontSize: 12 }, tickLine: false };
const RANGES = [7, 14, 30];

export default function DashboardPage() {
  const [days, setDays] = useState(14);

  const summary = useLoad(async () => {
    const get = (p) => api.get(`/api/reports/${p}`).then((r) => r.data);
    const [overview, workload, services, categories, priorities, sla] = await Promise.all([
      get('overview'), get('technician-workload'), get('service-analysis'), get('category-distribution'),
      get('priority-distribution'), get('sla-compliance'),
    ]);
    return { overview, workload, services, categories, priorities, sla };
  }, []);
  const perDay = useLoad(async () => (await api.get('/api/reports/incidents-per-day', { params: { days } })).data, [days]);

  const refresh = () => { summary.reload(); perDay.reload(); };
  const D = summary.data;

  return (
    <>
      <PageHeader title="Dashboard" subtitle="How the service desk is doing right now.">
        <button type="button" className="btn" onClick={refresh} disabled={summary.loading}>Refresh</button>
      </PageHeader>
      <ErrorBox error={summary.error} />
      {summary.loading && !D ? <Loading what="reports" /> : D && (
        <>
          <Overview o={D.overview} />

          <div className="chart-grid">
            <ChartCard title="Incidents reported per day" wide
              controls={(
                <div className="segmented segmented-small" role="group" aria-label="Time range">
                  {RANGES.map((r) => (
                    <button key={r} type="button" className={days === r ? 'selected' : ''} onClick={() => setDays(r)}>{r} days</button>
                  ))}
                </div>
              )}
              table={perDay.data && { head: ['Date', 'Incidents'], rows: perDay.data.map((d) => [d.date, d.count]) }}>
              <ErrorBox error={perDay.error} />
              {perDay.data && <PerDayChart data={perDay.data} />}
            </ChartCard>

            <ChartCard title="SLA compliance" subtitle="All incidents, by SLA state"
              table={{ head: ['SLA state', 'Incidents'], rows: D.sla.map((s) => [SLA_META[s.slaStatus]?.text || s.slaStatus, s.count]) }}>
              <SlaChart data={D.sla} />
            </ChartCard>

            <ChartCard title="Incidents by priority"
              table={{ head: ['Priority', 'Incidents'], rows: D.priorities.map((p) => [p.priority, p.count]) }}>
              <SimpleBars data={D.priorities.map((p) => ({ name: p.priority, value: p.count }))} vertical={false} />
            </ChartCard>

            <ChartCard title="Technician workload" subtitle="Active incidents, against each person's limit"
              table={{ head: ['Technician', 'Active', 'Limit'], rows: D.workload.map((w) => [w.name, w.active, w.max]) }}>
              <WorkloadChart data={D.workload} />
            </ChartCard>

            <ChartCard title="Incidents by service"
              table={{ head: ['Service', 'Incidents'], rows: D.services.map((s) => [s.name, s.count]) }}>
              <SimpleBars data={D.services.map((s) => ({ name: s.name, value: s.count }))} vertical />
            </ChartCard>

            <ChartCard title="Incidents by category"
              table={{ head: ['Category', 'Incidents'], rows: D.categories.map((c) => [c.name, c.count]) }}>
              <SimpleBars data={D.categories.map((c) => ({ name: c.name, value: c.count }))} vertical />
            </ChartCard>
          </div>
        </>
      )}
    </>
  );
}

function Overview({ o }) {
  const tiles = [
    { label: 'Total incidents', value: o.total, to: '/incidents' },
    { label: 'Open', value: o.open },
    { label: 'Open P1 (critical)', value: o.critical, to: '/incidents?priority=P1' },
    { label: 'Resolved', value: o.resolved },
    { label: 'SLA violations', value: o.slaViolations, to: '/incidents?slaStatus=VIOLATED', alert: o.slaViolations > 0 },
    { label: 'Average rating', value: o.averageRating == null ? '–' : `${o.averageRating.toFixed(1)} / 5` },
  ];
  return (
    <div className="stat-row">
      {tiles.map((t) => {
        const body = (
          <>
            <div className="tile-label">{t.label}</div>
            <div className="tile-value">
              {t.alert && <span className="stat-icon" aria-hidden="true">✕</span>}{t.value}
            </div>
          </>
        );
        return t.to
          ? <Link key={t.label} to={t.to} className={`card stat${t.alert ? ' stat-alert' : ''}`}>{body}</Link>
          : <div key={t.label} className="card stat">{body}</div>;
      })}
    </div>
  );
}

/** A chart with its title, optional controls and a table view of the same numbers. */
function ChartCard({ title, subtitle, controls, table, wide, children }) {
  const [showTable, setShowTable] = useState(false);
  return (
    <section className={`card chart-card${wide ? ' chart-wide' : ''}`}>
      <div className="chart-head">
        <div>
          <h2>{title}</h2>
          {subtitle && <p className="muted small">{subtitle}</p>}
        </div>
        <div className="chart-controls">
          {controls}
          {table && (
            <button type="button" className="link-button" onClick={() => setShowTable(!showTable)} aria-pressed={showTable}>
              {showTable ? 'Show chart' : 'Show table'}
            </button>
          )}
        </div>
      </div>
      {showTable && table ? (
        <div className="table-wrap chart-table">
          <table className="table compact">
            <thead><tr>{table.head.map((h) => <th key={h}>{h}</th>)}</tr></thead>
            <tbody>
              {table.rows.length === 0
                ? <tr><td colSpan={table.head.length} className="muted">No data.</td></tr>
                : table.rows.map((r) => <tr key={r[0]}>{r.map((c, i) => <td key={i}>{c}</td>)}</tr>)}
            </tbody>
          </table>
        </div>
      ) : children}
    </section>
  );
}

function ChartTooltip({ active, payload, label: name, render }) {
  if (!active || !payload?.length) return null;
  return <div className="chart-tooltip">{render ? render(payload[0].payload, name) : <><strong>{name}</strong><div>{payload[0].value}</div></>}</div>;
}

/** A value label drawn just past the end of a horizontal bar, on one line however short the bar is. */
function EndLabel({ x, y, width, height, value }) {
  return <text x={x + width + 6} y={y + height / 2} dy={4} fill="var(--text-secondary)" fontSize={12}>{value}</text>;
}

function NoData() {
  return <div className="empty">No incidents yet.</div>;
}

function PerDayChart({ data }) {
  if (!data.length) return <NoData />;
  const short = (d) => new Date(`${d}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
  return (
    <ResponsiveContainer width="100%" height={240}>
      <LineChart data={data} margin={{ top: 12, right: 16, bottom: 0, left: -12 }}>
        <CartesianGrid vertical={false} stroke="var(--grid)" />
        <XAxis dataKey="date" tickFormatter={short} {...AXIS} minTickGap={24} />
        <YAxis allowDecimals={false} {...AXIS} axisLine={false} width={40} />
        <Tooltip cursor={{ stroke: 'var(--text-muted)', strokeDasharray: '3 3' }}
          content={<ChartTooltip render={(p) => <><strong>{short(p.date)}</strong><div>{p.count} incident{p.count === 1 ? '' : 's'}</div></>} />} />
        <Line type="linear" dataKey="count" stroke={SERIES} strokeWidth={2} dot={{ r: 3, fill: SERIES, strokeWidth: 0 }}
          activeDot={{ r: 5, stroke: 'var(--surface)', strokeWidth: 2 }} isAnimationActive={false} />
      </LineChart>
    </ResponsiveContainer>
  );
}

/** Bars of one series in one colour. vertical = horizontal bars with the names on the left. */
function SimpleBars({ data, vertical }) {
  if (!data.length || data.every((d) => d.value === 0)) return <NoData />;
  const height = vertical ? Math.max(160, data.length * 34 + 30) : 240;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout={vertical ? 'vertical' : 'horizontal'} barCategoryGap={vertical ? 8 : '28%'}
        margin={{ top: 16, right: 32, bottom: 0, left: vertical ? 8 : -12 }}>
        <CartesianGrid horizontal={!vertical} vertical={vertical} stroke="var(--grid)" />
        {vertical ? (
          <>
            <XAxis type="number" allowDecimals={false} {...AXIS} />
            <YAxis type="category" dataKey="name" {...AXIS} width={130} axisLine={false} />
          </>
        ) : (
          <>
            <XAxis dataKey="name" {...AXIS} />
            <YAxis allowDecimals={false} {...AXIS} axisLine={false} width={40} />
          </>
        )}
        <Tooltip cursor={{ fill: 'var(--hover)' }} content={<ChartTooltip />} />
        <Bar dataKey="value" fill={SERIES} radius={vertical ? [0, 4, 4, 0] : [4, 4, 0, 0]} maxBarSize={vertical ? 20 : 48} isAnimationActive={false}>
          {vertical
            ? <LabelList dataKey="value" content={EndLabel} />
            : <LabelList dataKey="value" position="top" fill="var(--text-secondary)" fontSize={12} />}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

/** SLA states use the reserved status colours, always with an icon and a word on the axis. */
function SlaChart({ data }) {
  const rows = ['ON_TRACK', 'AT_RISK', 'VIOLATED'].map((k) => ({
    key: k, name: `${SLA_META[k].icon} ${SLA_META[k].text}`, value: data.find((d) => d.slaStatus === k)?.count ?? 0,
  }));
  if (rows.every((r) => r.value === 0)) return <NoData />;
  return (
    <ResponsiveContainer width="100%" height={200}>
      <BarChart data={rows} layout="vertical" barCategoryGap={10} margin={{ top: 8, right: 32, bottom: 0, left: 8 }}>
        <CartesianGrid horizontal={false} stroke="var(--grid)" />
        <XAxis type="number" allowDecimals={false} {...AXIS} />
        <YAxis type="category" dataKey="name" {...AXIS} width={96} axisLine={false} />
        <Tooltip cursor={{ fill: 'var(--hover)' }} content={<ChartTooltip />} />
        <Bar dataKey="value" radius={[0, 4, 4, 0]} maxBarSize={22} isAnimationActive={false}>
          {rows.map((r) => <Cell key={r.key} fill={SLA_META[r.key].color} />)}
          <LabelList dataKey="value" content={EndLabel} />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

function WorkloadChart({ data }) {
  if (!data.length) return <NoData />;
  const rows = data.map((w) => ({ ...w, value: w.active, label: `${w.active} / ${w.max}` }));
  return (
    <ResponsiveContainer width="100%" height={Math.max(160, rows.length * 34 + 30)}>
      <BarChart data={rows} layout="vertical" barCategoryGap={8} margin={{ top: 8, right: 48, bottom: 0, left: 8 }}>
        <CartesianGrid horizontal={false} stroke="var(--grid)" />
        <XAxis type="number" allowDecimals={false} domain={[0, (max) => Math.max(max, ...rows.map((r) => r.max))]} {...AXIS} />
        <YAxis type="category" dataKey="name" {...AXIS} width={110} axisLine={false} />
        <Tooltip cursor={{ fill: 'var(--hover)' }}
          content={<ChartTooltip render={(p) => <><strong>{p.name}</strong><div>{p.active} active of {p.max} allowed</div></>} />} />
        <Bar dataKey="value" fill={SERIES} radius={[0, 4, 4, 0]} maxBarSize={20} minPointSize={2} isAnimationActive={false}>
          <LabelList dataKey="label" content={EndLabel} />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
