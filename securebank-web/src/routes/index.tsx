import { createBrowserRouter } from 'react-router'
import { Home } from './Home'
import { NotFound } from './NotFound'

export const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '*', element: <NotFound /> },
])
