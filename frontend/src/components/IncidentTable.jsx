import { Link } from 'react-router-dom';
import { PriorityBadge, SlaBadge, StatusBadge, Empty } from './ui';
import { formatDateTime, relative, TERMINAL } from '../lib/format';

/** The incident list used by every role. Columns can be hidden for pages where they add nothing. */
export default function IncidentTable({ incidents, hide = [], emptyText = 'No incidents found.' }) {
  if (!incidents.length) return <Empty>{emptyText}</Empty>;
  const show = (col) => !hide.includes(col);
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Number</th>
            <th>Title</th>
            <th>Priority</th>
            <th>Status</th>
            <th>SLA</th>
            {show('service') && <th>Service</th>}
            {show('reporter') && <th>Reporter</th>}
            {show('assignee') && <th>Assignee</th>}
            {show('due') && <th>Resolve by</th>}
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          {incidents.map((i) => (
            <tr key={i.id}>
              <td><Link to={`/incidents/${i.id}`} className="mono">{i.incidentNumber}</Link></td>
              <td className="cell-title"><Link to={`/incidents/${i.id}`}>{i.title}</Link></td>
              <td><PriorityBadge priority={i.priority} /></td>
              <td><StatusBadge status={i.status} /></td>
              <td><SlaBadge sla={i.slaStatus} /></td>
              {show('service') && <td>{i.service?.name}</td>}
              {show('reporter') && <td>{i.reporter?.name}</td>}
              {show('assignee') && <td>{i.assignee?.name || <span className="muted">Unassigned</span>}</td>}
              {show('due') && (
                <td title={formatDateTime(i.resolutionDeadline)}>
                  {TERMINAL.includes(i.status) || !i.resolutionDeadline ? <span className="muted">–</span> : relative(i.resolutionDeadline)}
                </td>
              )}
              <td className="nowrap">{formatDateTime(i.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
