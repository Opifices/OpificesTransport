# Opifices Transport v2.0
# The Ruby Brain - Hot-Reloadable Logic
# This file is watched by the Java core. Changes take effect immediately.

class OpificesBrain
  def on_swarm_update(peers, progress, download_rate)
    # Log inputs (Java objects wrapped in Ruby)
    # Java::BtNet::Peer objects
    
    peer_count = peers.size
    
    if rand < 0.05 # Reduce log spam (approx every 25 ticks)
      puts "[RUBY-BRAIN] Thinking... Peers: #{peer_count}, Progress: #{progress}%, Speed: #{download_rate} B/s"
    end

    if progress > 98.0
      puts "[RUBY-BRAIN] ENDGAME SCENARIO DETECTED. Ruby advises: PANIC MODE."
      # DSL: peers.each { |p| p.set_priority(:high) }
    elsif peer_count < 3
       puts "[RUBY-BRAIN] Low swarm health. Ruby advises: Aggressive Peer Bias."
    end
    
    # Example: Dynamic Choking logic
    # peers.each do |p|
    #   if p.meta['country'] == 'JP'
    #      puts "[RUBY] Found Japanese peer, prioritizing..."
    #   end
    # end
  end
end

# Instantiate the brain and assign to global variable expected by Java bridge
$brain_instance = OpificesBrain.new
puts "[RUBY-BRAIN] Logic loaded successfully."
