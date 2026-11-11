import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const rawSession = localStorage.getItem('cafeteria_session');
  if (rawSession) {
    try {
      const session = JSON.parse(rawSession);
      if (session?.token) {
        req = req.clone({
          setHeaders: {
            Authorization: `Bearer ${session.token}`
          }
        });
      }
    } catch {
      // ignore corrupted storage
    }
  }
  return next(req);
};
