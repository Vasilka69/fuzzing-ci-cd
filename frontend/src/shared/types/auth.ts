export type AuthLoginRequest = {
  login: string;
  password: string;
};

export type AuthLoginResponse = {
  userId: string;
  login: string;
  token: string;
  tokenType: string;
};

export type AuthMeResponse = {
  userId: string | null;
  login: string | null;
  roles: string[];
};

export type AuthSession = {
  userId: string;
  login: string;
  token: string;
};
