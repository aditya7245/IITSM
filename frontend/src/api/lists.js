import api from './client';

// Small lookups several pages need. The backend caps a page at 100 rows, which is plenty for a course demo.

export async function fetchTechnicians() {
  const [techs, leads] = await Promise.all([
    api.get('/api/admin/users', { params: { role: 'TECHNICIAN', size: 100 } }),
    api.get('/api/admin/users', { params: { role: 'TEAM_LEAD', size: 100 } }),
  ]);
  return [...techs.data.content, ...leads.data.content].sort((a, b) => a.fullName.localeCompare(b.fullName));
}

export async function fetchAllUsers() {
  const { data } = await api.get('/api/admin/users', { params: { size: 100 } });
  return [...data.content].sort((a, b) => a.fullName.localeCompare(b.fullName));
}

/** Finds an incident from its number (INC1001), or null if there is no exact match the caller may see. */
export async function findIncidentByNumber(number) {
  const clean = number.trim().toUpperCase();
  if (!clean) return null;
  const { data } = await api.get('/api/incidents', { params: { incidentNumber: clean, size: 20 } });
  return data.content.find((i) => i.incidentNumber.toUpperCase() === clean) || null;
}
