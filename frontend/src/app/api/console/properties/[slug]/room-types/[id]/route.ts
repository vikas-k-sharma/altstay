import { proxy } from '@/lib/server/proxy';
import { UpdateRoomTypeRequestSchema, RoomTypeDtoSchema } from '@/lib/contracts/inventory';

export const PUT = proxy({
  method: 'PUT',
  path: (req) => {
    const segments = req.nextUrl.pathname.split('/');
    const [slug, id] = [segments.at(-3), segments.at(-1)];
    return `/api/v1/properties/${slug}/room-types/${id}`;
  },
  requestSchema: UpdateRoomTypeRequestSchema,
  responseSchema: RoomTypeDtoSchema,
});
