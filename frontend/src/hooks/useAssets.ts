import { useQuery } from '@tanstack/react-query'
import {
  getAsset,
  getAssetRelations,
  getFavorite,
  getComments,
  searchAssets,
  getEquipmentInterconnections,
} from '../services/assetService'
import type { AssetSearchParams } from '../types/asset'

export function useAssetSearch(params: AssetSearchParams) {
  return useQuery({
    queryKey: ['assets', params],
    queryFn: () => searchAssets(params),
    placeholderData: (previousData) => previousData,
  })
}

export function useAsset(id?: number) {
  return useQuery({
    queryKey: ['asset', id],
    queryFn: () => getAsset(id!),
    enabled: id !== undefined,
  })
}

export function useAssetRelations(id?: number) {
  return useQuery({
    queryKey: ['asset-relations', id],
    queryFn: () => getAssetRelations(id!),
    enabled: id !== undefined,
  })
}

export function useFavorite(id?: number) {
  return useQuery({
    queryKey: ['asset-favorite', id],
    queryFn: () => getFavorite(id!),
    enabled: id !== undefined,
  })
}

export function useComments(id?: number) {
  return useQuery({
    queryKey: ['asset-comments', id],
    queryFn: () => getComments(id!),
    enabled: id !== undefined,
  })
}

export function useEquipmentInterconnections(equipmentCode?: string) {
  return useQuery({
    queryKey: ['equipment-interconnections', equipmentCode],
    queryFn: () => getEquipmentInterconnections(equipmentCode),
    enabled: Boolean(equipmentCode),
  })
}
