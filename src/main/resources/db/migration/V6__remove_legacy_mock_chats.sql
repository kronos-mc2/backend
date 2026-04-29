DELETE FROM chat_rooms cr
WHERE cr.id IN ('c1', 'c2', 'c3')
  AND NOT EXISTS (
    SELECT 1
    FROM chat_members cm
    WHERE cm.room_id = cr.id
  );

DELETE FROM conversations
WHERE id IN ('c1', 'c2', 'c3');
