static void vrend_resource_buffer_copy(UNUSED struct vrend_context *ctx,
                                       struct vrend_resource *src_res,
                                       struct vrend_resource *dst_res,
                                       uint32_t dstx, uint32_t srcx,
                                       uint32_t width)
{
   glBindBuffer(GL_COPY_READ_BUFFER, src_res->gl_id);
   glBindBuffer(GL_COPY_WRITE_BUFFER, dst_res->gl_id);

   glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, srcx, dstx, width);
   glBindBuffer(GL_COPY_READ_BUFFER, 0);
   glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
}
