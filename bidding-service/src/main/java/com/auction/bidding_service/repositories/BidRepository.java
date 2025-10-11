package com.auction.bidding_service.repositories;

import com.auction.bidding_service.models.Bid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

  Optional<Bid> findFirstByItemIdOrderByBidAmountDesc(Long itemId);

  List<Bid> findByBidderId(UUID bidderId);

  Page<Bid> findByItemIdOrderByTimestampDesc(Long itemId, Pageable pageable);

  List<Bid> findByItemIdAndBidderIdOrderByTimestampDesc(Long itemId, UUID bidderId);

  long countByItemId(Long itemId);


}
