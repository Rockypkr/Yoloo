package com.yoloo.backend.feed;

import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.users.User;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;
import com.yoloo.backend.account.Account;
import com.yoloo.backend.base.Controller;
import com.yoloo.backend.post.Post;
import com.yoloo.backend.post.PostShardService;
import com.yoloo.backend.vote.VoteService;
import java.util.List;
import java.util.logging.Logger;
import lombok.AllArgsConstructor;

import static com.yoloo.backend.OfyService.ofy;

@AllArgsConstructor(staticName = "create")
public class FeedController extends Controller {

  private static final Logger LOG =
      Logger.getLogger(FeedController.class.getName());

  /**
   * Maximum number of questions to return.
   */
  private static final int DEFAULT_LIST_LIMIT = 20;

  private PostShardService postShardService;

  private VoteService voteService;

  /**
   * List feed collection response.
   *
   * @param limit the limit
   * @param cursor the cursor
   * @param user the user
   * @return the collection response
   */
  public CollectionResponse<Post> listFeed(Optional<Integer> limit, Optional<String> cursor,
      User user) {

    // Create account key from websafe id.
    final Key<Account> accountKey = Key.create(user.getUserId());

    Query<Feed> query = getFeedQuery(limit, cursor, accountKey);

    final QueryResultIterator<Feed> qi = query.iterator();

    List<Post> feed = Lists.newArrayListWithCapacity(DEFAULT_LIST_LIMIT);

    while (qi.hasNext()) {
      feed.add(qi.next().getFeedItem());
    }

    feed = postShardService.mergeShards(feed)
        .flatMap(posts -> voteService.checkPostVote(posts, accountKey, false))
        .blockingSingle();

    return CollectionResponse.<Post>builder()
        .setItems(feed)
        .setNextPageToken(qi.getCursor().toWebSafeString())
        .build();
  }

  private Query<Feed> getFeedQuery(Optional<Integer> limit, Optional<String> cursor,
      Key<Account> accountKey) {

    Query<Feed> query = ofy()
        .load()
        .group(Post.ShardGroup.class)
        .type(Feed.class)
        .ancestor(accountKey);

    // Fetch items from beginning from cursor.
    query = cursor.isPresent()
        ? query.startAt(Cursor.fromWebSafeString(cursor.get()))
        : query;

    query = query.limit(limit.or(DEFAULT_LIST_LIMIT));

    return query;
  }
}