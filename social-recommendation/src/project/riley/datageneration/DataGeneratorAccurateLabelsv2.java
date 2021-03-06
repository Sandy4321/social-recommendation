/** Generate .arff file of (uid, link_id, like/dislike, vec(features)) from App data
 * 
 * @author Riley Kidd
 * @author Scott Sanner
 */

package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.nicta.lr.util.*;

import project.riley.messagefrequency.UserUtil;

public class DataGeneratorAccurateLabelsv2 {

	public static final String YES = "'y'".intern();
	public static final String NO  = "'n'".intern();
	public static final String[] RATINGS = new String[]{ YES, NO };

	/*
	 * For caching data
	 */
	public static ArrayList<EInteractionType> _featuresInt = null;
	public static ArrayList<EDirectionType>   _featuresDir = null;
	public static Map<Long,Set<Long>> _uid2all_passive_linkids_likes = null;
	public static Map<Long,Set<Long>> _uid2linkids_likes = null;
	public static Map<Long,Set<Long>> _uid2linkids_dislikes = null;
	public static Map<EInteractionType,Map<EDirectionType,Map<Long,Set<Long>>>> _int_dir2uid_linkid = null;

	/*
	 * Generate data for accurately labeled data from NICTA app
	 */
	public static PrintWriter _writer;

	public static void populateCachedData(boolean active_likes, int top_k) throws SQLException {

		////////////////////////////////////////////////////////////////////////
		// Get dataset of likes / dislikes for active or passive
		////////////////////////////////////////////////////////////////////////
		_uid2all_passive_linkids_likes = UserUtil.getLikes(ELikeType.LINK);
		_uid2linkids_likes = new HashMap<Long,Set<Long>>();
		_uid2linkids_dislikes = new HashMap<Long,Set<Long>>();
		
		if (active_likes) {

			String userQuery = "select uid, link_id, rating from trackRecommendedLinks where rating != 0;"; // 0 = not rated
			Statement statement = SQLUtil.getStatement();

			ResultSet result = statement.executeQuery(userQuery);
			while (result.next()) {
				long uid = result.getLong(1);
				long link_id = result.getLong(2);
				// 0 = not rated, 1 = liked, 2 = not liked
				int rating = result.getInt(3);

				// SPS - cannot do this yet... 0 should be thrown out first
				// userLikes.put(link_id, (rating == 1 ? "y" : "n"));
				if (rating < 1 || rating > 2)
					continue;
				boolean is_liked = (rating == 1);

				Set<Long> userLikes = (is_liked ? _uid2linkids_likes : _uid2linkids_dislikes).get(uid);
				if (userLikes == null){
					userLikes = new HashSet<Long>();
					(is_liked ? _uid2linkids_likes : _uid2linkids_dislikes).put(uid, userLikes);
				}
				userLikes.add(link_id);
			}

		} else {
			//System.out.println("Cannot handle passive likes yet");
			//System.exit(1);
			//_uid2linkids_likes = _uid2all_passive_linkids_likes;
			// passive Facebook data
			// rank top k, filter and infer dislikes

			Map<Long,Set<Long>> knownLikes = UserUtil.getLikes(ELikeType.ALL);		// all user likes
			TreeMap<Long,Integer> topLikes = topLiked(knownLikes);					// sorted user likes
			for (Long like_item : topLikes.keySet()){								// top liked 'things'
				for (Long uid : UserUtil.getAppUserIds()){							// each app user
					//System.out.println(key + " " + topLikes.get(key));
					boolean is_liked = false;
					
					//System.out.println(uid + " " + ExtractRelTables.UID_2_NAME.get(uid));      <--------------- why null?
					
					if (knownLikes.get(uid) != null && knownLikes.get(uid).contains(like_item)){
						is_liked = true;
					}					

					Set<Long> userLikes = (is_liked ? _uid2linkids_likes : _uid2linkids_dislikes).get(uid);
					if (userLikes == null){
						userLikes = new HashSet<Long>();
						(is_liked ? _uid2linkids_likes : _uid2linkids_dislikes).put(uid, userLikes);
					}
					userLikes.add(like_item);
					
					top_k--;
					if (top_k <= 0) break;
				}
			}

		}

		////////////////////////////////////////////////////////////////////////
		// For every user and interaction type/dir, get set of items liked by 
		//   that set of alters (threshold for now at 1)
		////////////////////////////////////////////////////////////////////////
		_featuresInt = new ArrayList<EInteractionType>();
		_featuresDir = new ArrayList<EDirectionType>();
		_int_dir2uid_linkid = new HashMap<EInteractionType,Map<EDirectionType,Map<Long,Set<Long>>>>();
		for (EInteractionType itype : EInteractionType.values()) {

			// Don't need interactions beyond real
			if (itype == EInteractionType.REAL)
				break;

			Map<EDirectionType,Map<Long,Set<Long>>> dir2rest = _int_dir2uid_linkid.get(itype);
			if (dir2rest == null) {
				dir2rest = new HashMap<EDirectionType,Map<Long,Set<Long>>>();
				_int_dir2uid_linkid.put(itype, dir2rest);
			}

			for (EDirectionType dir : EDirectionType.values()) {

				if (dir == EDirectionType.BIDIR)
					continue;
				else if (itype == EInteractionType.FRIENDS && dir == EDirectionType.OUTGOING)
					continue;

				// Maintain feature list in parallel arrays of int/dir
				_featuresInt.add(itype);
				_featuresDir.add(dir);

				Map<Long,Set<Long>> uid2rest = dir2rest.get(dir);
				if (uid2rest == null) {
					uid2rest = new HashMap<Long,Set<Long>>();
					dir2rest.put(dir, uid2rest);
				}

				Interaction i = UserUtil.getUserInteractions(itype.toString(), dir);

				System.out.println("**********************\n" + _featuresInt + "\n" + _featuresDir);
				System.out.println("**********************\n" + itype + "_" + dir + " #alters = " + i.getAllInteractions().size());

				for (Long uid : _uid2linkids_likes.keySet()) {

					// ** Note: these are passive likes on Facebook!
					Set<Long> alters = i.getInteractions(uid);
					System.out.println(ExtractRelTables.UID_2_NAME.get(uid) + " " + itype + "_" + dir + " #alters = " + (alters == null ? 0 : alters.size()));

					HashMap<Long,Integer> other_likes_id2count = ExtractRelTables.GetLikesInteractions(uid, i, _uid2all_passive_linkids_likes);
					//P(like | friend likes) = P(like and friend likes) / P(friend likes)
					//                       = F(like and friend likes) / F(friend likes)
					Set<Long> other_likes_ids = ExtractRelTables.ThresholdAtK(other_likes_id2count, /*k*/1);
					if (other_likes_ids == null)
						other_likes_ids = new HashSet<Long>();
					uid2rest.put(uid, other_likes_ids);

					System.out.println("-- count of set of links liked by alters under uid,itype,dir: " + other_likes_ids.size());
				}
			}
		}
	}

	/*
	 * Each liked items count of likes from app user base
	 */
	public static TreeMap<Long, Integer> topLiked(Map<Long,Set<Long>> allLikes){		
		final HashMap<Long, Integer> topLiked = new HashMap<Long,Integer>();
		for (Long uid : allLikes.keySet()){
			for (Long likes : allLikes.get(uid)){
				Integer totalLiked = topLiked.get(likes);
				if (totalLiked == null){
					topLiked.put(likes, 1);
				} else {
					int update = totalLiked + 1;
					topLiked.put(likes, update);
				}
			}
		}

		Comparator<Long> vc = new Comparator<Long>(){
			@Override
			public int compare(Long a, Long b) {
				int compare = topLiked.get(b) - topLiked.get(a);
				if (compare == 0) return a.compareTo(b);
				else return compare;
			}						
		};

		TreeMap<Long, Integer> sortedLikes = new TreeMap(vc);
		sortedLikes.putAll(topLiked);

		/*for (Long key : sortedLikes.keySet()){
			System.out.println(key + ":" + topLiked.get(key));
		}		*/
		return sortedLikes;	
	}

	/*
	 * Write arff header data
	 */
	public static void writeHeader(String fileName) throws Exception {
		_writer = new PrintWriter(fileName);		
		_writer.println("@relation app-data");
		_writer.println("@attribute 'Uid' numeric");
		_writer.println("@attribute 'Item' numeric");
		_writer.println("@attribute 'Class' { 'n' , 'y' }");
		for (int feat_index = 0; feat_index < _featuresInt.size(); feat_index++) {
			_writer.println("@attribute '" + 
					_featuresDir.get(feat_index) + "_" + 
					_featuresInt.get(feat_index) + "' { 'n', 'y' }");
		}

		_writer.println("@data");
	}

	/*
	 * Write known rating data
	 */
	public static void writeData(String filename, int interaction_threshold) throws Exception {

		writeHeader(filename);

		System.out.println("Extracting ratings data for " + _uid2linkids_likes.size() + " users");

		for (String rating : RATINGS) {

			Map<Long,Set<Long>> uid2links = 
					(rating == YES ? _uid2linkids_likes : _uid2linkids_dislikes); 

			for (Entry<Long, Set<Long>> entry : uid2links.entrySet()){
				Long uid = entry.getKey();
				Set<Long> link_ids = entry.getValue();
				System.out.println("User " + ExtractRelTables.UID_2_NAME.get(uid) + " made " + link_ids.size() + " " + rating + " ratings");
				for (Long link_id : link_ids){
					StringBuffer feature_vector = new StringBuffer(uid + "," + link_id + "," + rating);
					int count = 0;

					//_writer.print(uid + "," + link_id + "," + rating);

					// Now write columns
					for (int feat_index = 0; feat_index < _featuresInt.size(); feat_index++) {
						//System.out.println("Writing feature: " + _featuresDir.get(feat_index) + "_" + _featuresInt.get(feat_index));
						Set<Long> alter_likes = _int_dir2uid_linkid.get(_featuresInt.get(feat_index)).get(_featuresDir.get(feat_index)).get(uid);
						String feat_value = alter_likes == null ? NO : (alter_likes.contains(link_id) ? YES : NO);
						//_writer.print("," + feat_value);
						feature_vector.append("," + feat_value);
						if (feat_value == YES){
							count++;
						}
					}
					if (count >= interaction_threshold){
						_writer.println(feature_vector.toString());
					}
				}
			}
		}

		_writer.close();
	}

	/*
	 * Return whether a specific user likes an item
	 */
	public static boolean userLikes(Long uid, Long lid, String row, String table) throws SQLException{
		Statement statement = SQLUtil.getStatement();
		String userQuery = "SELECT count(*) FROM " + table + " WHERE uid = " + uid + " AND " + row + " = " + lid;
		ResultSet result = statement.executeQuery(userQuery);
		result.next();			
		return (result.getInt(1) == 0 ? false : true);
	}


	public static void main(String[] args) throws Exception {
		//populateCachedData(true /* active */,10,/* top k */);
		//writeData("active_data.arff",/* interaction threshold */ 1);
		populateCachedData(false /* active */,10 /* top k */);
		writeData("passive_data.arff",/* interaction threshold */ 1);
	}

}
