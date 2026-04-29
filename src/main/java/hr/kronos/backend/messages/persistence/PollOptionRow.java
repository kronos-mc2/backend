package hr.kronos.backend.messages.persistence;

public class PollOptionRow {
  private String id;
  private String pollId;
  private String text;
  private Integer sortOrder;
  private Integer voteCount;
  private Boolean votedByMe;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPollId() {
    return pollId;
  }

  public void setPollId(String pollId) {
    this.pollId = pollId;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(Integer sortOrder) {
    this.sortOrder = sortOrder;
  }

  public Integer getVoteCount() {
    return voteCount;
  }

  public void setVoteCount(Integer voteCount) {
    this.voteCount = voteCount;
  }

  public Boolean getVotedByMe() {
    return votedByMe;
  }

  public void setVotedByMe(Boolean votedByMe) {
    this.votedByMe = votedByMe;
  }
}
