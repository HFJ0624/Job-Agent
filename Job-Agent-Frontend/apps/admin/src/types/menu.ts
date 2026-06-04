export interface AdminMenuItem {
  id: number;
  path: string;
  name: string;
  title: string;
  icon: string;
  component?: string;
  hidden?: boolean;
  children?: AdminMenuItem[];
}
