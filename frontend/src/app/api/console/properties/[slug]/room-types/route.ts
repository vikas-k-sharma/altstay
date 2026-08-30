import { proxy } from '@/lib/server/proxy';
import { CreateRoomTypeRequestSchema, RoomTypeDtoSchema } from '@/lib/contracts/inventory';

export const POST = proxy({
  method: 'POST',
  path: (req) => {
    const slug = req.nextUrl.pathname.split('/').at(-2);
    return `/api/v1/properties/${slug}/room-types`;
  },
  requestSchema: CreateRoomTypeRequestSchema,
  responseSchema: RoomTypeDtoSchema,
});
