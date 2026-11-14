// Role groups used by routing and navigation. A team lead is a technician; an IT manager is an administrator.
export const ROLE_GROUPS = {
  employee: ['EMPLOYEE'],
  technician: ['TECHNICIAN', 'TEAM_LEAD'],
  admin: ['ADMINISTRATOR', 'IT_MANAGER'],
};

export const HOME = {
  employee: '/my-incidents',
  technician: '/work',
  admin: '/dashboard',
};

export const ROLES = ['EMPLOYEE', 'TECHNICIAN', 'TEAM_LEAD', 'ADMINISTRATOR', 'IT_MANAGER'];

export function groupOf(role) {
  return Object.keys(ROLE_GROUPS).find((group) => ROLE_GROUPS[group].includes(role)) || null;
}

export function isTechnicianRole(role) {
  return ROLE_GROUPS.technician.includes(role);
}
