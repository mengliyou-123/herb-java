package org.herb.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.herb.mapper.CommentMapper;
import org.herb.pojo.Comment;
import org.herb.pojo.PageBean;
import org.herb.pojo.Post;
import org.herb.service.CommentService;
import org.herb.utils.ThreadLocalUtil;
import org.herb.utils.CurrentUser;
import org.herb.exception.NotFoundException;
import org.herb.mapper.PostMapper;
import org.herb.pojo.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CommentServiceImpl implements CommentService {
    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostMapper postMapper;

    @Override
    @Transactional
    public void publish(Comment comment) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer publisherId = (Integer)map.get("id");
        comment.setPublisherId(publisherId);
        commentMapper.post(comment);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Comment existing = commentMapper.getCommentById(id);
        if (existing == null) throw new NotFoundException("评论不存在");
        CurrentUser.requireOwnerOrAdmin(existing.getPublisherId());
        commentMapper.delete(id);
    }

    @Override
    public List<Comment> list(Integer postId) {
        return commentMapper.list(postId);
    }

    @Override
    public Comment getCommentById(Integer id) {
        return commentMapper.getCommentById(id);
    }

    @Override
    @Transactional
    public void deleteByPostId(Integer postId) {
        Post post = postMapper.findById(postId);
        if (post == null) throw new NotFoundException("帖子不存在");
        CurrentUser.requireOwnerOrAdmin(post.getPosterId());
        commentMapper.deleteByPostId(postId);
    }

    @Override
    @Transactional
    public void deleteByUserId(Integer userId) {
        commentMapper.deleteByUserId(userId);
    }
}
