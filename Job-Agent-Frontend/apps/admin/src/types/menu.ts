export interface AdminMenuItem {
  id: number;
  path: string;
  name: string;
  title: string;
  icon: string;
  component?: string;
  roles?: string[];
  hidden?: boolean;
  children?: AdminMenuItem[];
}

export interface AdminUserProfile {
  id: number;
  name: string;
  role: "admin" | "operator";
}
