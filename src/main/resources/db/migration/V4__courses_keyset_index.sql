-- Supports cursor pagination of courses, newest first (GET /api/courses/scroll): the next page is an index
-- range scan starting right after the cursor's (created_at, id), however deep the client has scrolled.
-- status leads because students, most of the traffic, only ever see PUBLISHED courses.
CREATE INDEX idx_courses_status_created_at_id ON courses (status, created_at DESC, id DESC);
