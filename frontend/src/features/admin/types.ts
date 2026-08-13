export type SystemRole =
  | 'NORMAL_USER'
  | 'UPLOADER'
  | 'DOCUMENT_MAINTAINER'
  | 'CONTENT_ADMIN'
  | 'SYSTEM_ADMIN'

export interface SystemUserScope {
  id: number
  base: string
  productLine: string
}

export interface SystemUser {
  id: number
  userId: string
  name: string
  department: string
  roles: SystemRole[]
  scopes: SystemUserScope[]
  updatedAt: string
  version: number
}

export interface OperationLog {
  id: number
  actorUserId: string
  action: string
  targetType: string
  targetId: number
  detailJson: string
  createdAt: string
}
