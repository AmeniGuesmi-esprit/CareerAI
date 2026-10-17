export interface NavigationItem {
  id: string; title: string;
  type: 'item' | 'collapse' | 'group';
  translate?: string; icon?: string; hidden?: boolean;
  url?: string; classes?: string; external?: boolean;
  target?: boolean; breadcrumbs?: boolean;
  children?: NavigationItem[];
}

export const NavigationItems: NavigationItem[] = [
  {
    id: 'administration', title: 'Administration', type: 'group', icon: 'icon-navigation',
    children: [
      { id: 'admin-dashboard', title: 'Tableau de bord', type: 'item',
        classes: 'nav-item', url: '/backoffice/dashboard', icon: 'ti ti-dashboard', breadcrumbs: false },
      { id: 'admin-users', title: 'Utilisateurs', type: 'item',
        classes: 'nav-item', url: '/backoffice/users', icon: 'ti ti-users', breadcrumbs: false },
              { id: 'admin-lettres', title: 'Lettres de motivation', type: 'item',
        classes: 'nav-item', url: '/backoffice/lettres', icon: 'ti ti-mail-opened', breadcrumbs: false }
    ]
  }
];