package me.tripsit.mobile.factsheets;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ExpandableListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import me.tripsit.mobile.R;
import me.tripsit.mobile.TripMobileActivity;
import me.tripsit.mobile.builders.LayoutBuilder;
import me.tripsit.mobile.utils.CollectionUtils;
import me.tripsit.mobile.utils.StringUtils;
import me.tripsit.mobile.utils.ViewUtils;

/**
 * The factsheets activity is used to retrieve data about particular drugs from the tripbot API
 * @author Eddie Curtis
 */
public class Factsheets extends TripMobileActivity implements FactsheetsCallback {

    AutoCompleteTextView drugNameSearch;

    @Override
    public int getLayoutId() {
        return R.layout.activity_factsheets;
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		searchDrugNames();
        drugNameSearch = (AutoCompleteTextView) findViewById(R.id.drugNameSearch);
		setDrugNameSearchListeners();
	}

    private void setDrugNameSearchListeners() {
        drugNameSearch.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getString(R.string.factsheets_default_search).equals(drugNameSearch.getText().toString())) {
                    drugNameSearch.setText("");
                }
            }
        });
        drugNameSearch.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                searchDrug((String) parent.getItemAtPosition(position));
            }
        });
        drugNameSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if ( actionId == EditorInfo.IME_ACTION_DONE ) {
                    searchDrug(drugNameSearch.getText().toString());
                }
                return false;
            }
        });


	}
	
	public void clickSearch(View v) {
		Editable drugName = drugNameSearch.getText();
        searchDrug(drugName.toString());
	}

    @Override
    public void searchDrug(String drugName) {
        ViewUtils.hideViewsWithId(this, R.id.txt_drugDisclaimer, R.id.exlist_drugInfo, R.id.txt_drugName);
        ViewUtils.showViewsWithId(this, R.id.progress_factsheets);

        // Hide keyboard
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        drugNameSearch.clearFocus();

        // Search for drug
        new DrugInfoAsyncTask(this, this, drugName).execute(this);
    }

    @Override
    public void finishActivity() {
        finish();
    }

    @Override
    public void onDrugListComplete(List<String> drugNames) {
        ArrayAdapter<String> adapter = new DrugNamesAdapter(this, android.R.layout.simple_dropdown_item_1line, drugNames);
        drugNameSearch.setAdapter(adapter);
    }

    @Override
    public void onDrugSearchComplete(Drug drug) {
        ViewUtils.showViewsWithId(this, R.id.exlist_drugInfo, R.id.txt_drugName);
        ViewUtils.hideViewsWithId(this, R.id.progress_factsheets);
        if (drug != null) {
            updateDrugView(drug);
            drugNameSearch.setText(getString(R.string.factsheets_default_search));
            drugNameSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        drugNameSearch.setText("");
                    }
                }
            });
        }
    }

    @Override
    public void searchDrugNames() {
        new DrugNamesAsyncTask(this, this).execute(this);
    }

    private void updateDrugView(Drug drug) {
        TextView drugName = (TextView) findViewById(R.id.txt_drugName);
        String updatedDrugName = manipulateDrugName(drug.getName(), drug.getAliases());
        drugName.setText(updatedDrugName);

        ExpandableListView infoList = (ExpandableListView) findViewById(R.id.exlist_drugInfo);
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<String, List<String>>();

        // If we didn't already put the aliases in the name
        if (updatedDrugName.length() == drug.getName().length() && drug.getAliases().size() > 0) {
            map.put(getString(R.string.aliases), CollectionUtils.collectionToList(drug.getAliases()));
        }

        map.put(getString(R.string.summary), Arrays.asList(drug.getSummary()));
        map.put(getString(R.string.dose), Arrays.asList(drug.getDosages()));
        if (drug.getEffects() != null) {
            map.put(getString(R.string.effects), Arrays.asList(drug.getEffects()));
        }
        if (drug.getCategories().size() > 0) {
            map.put(getString(R.string.categories), CollectionUtils.collectionToList(drug.getCategories()));
        }

        List<String> timing = getDrugTimings(drug);
        if (timing.size() > 0) {
            map.put(getString(R.string.timing), timing);
        }
        if (drug.getWiki() != null) {
            map.put(getString(R.string.wiki), Arrays.asList(drug.getWiki()));
        }
        for (Entry<String, String> entry : drug.getOtherInfo().entrySet()) {
            map.put(capitalise(entry.getKey()), Arrays.asList(entry.getValue()));
        }
        infoList.setAdapter(new DrugListAdapter(this, map));
    }

    private String manipulateDrugName(String drugName, Set<String> set) {
        String name = StringUtils.formatDrugName(drugName);
        if (set.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(' ').append('(');
            for (String s : set) {
                sb.append(s).append(',').append(' ');
            }

            int dimensionPixelSize = calculateMaxTextWidth();

            if (name.length() + sb.length() < dimensionPixelSize) {
                name = name + sb.toString().substring(0, sb.length() - 2) + ')';
            }
        }

        return name;
    }

    private int calculateMaxTextWidth() {
        int dimensionPixelSize = getResources().getDimensionPixelSize(R.dimen.factsheets_text_width);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            dimensionPixelSize *= 2;
        }
        return dimensionPixelSize;
    }

    private List<String> getDrugTimings(Drug drug) {
        List<String> timing = new ArrayList<String>();
        if (drug.getOnset() != null) {
            timing.add(getString(R.string.onset) + drug.getOnset());
        }
        if (drug.getDuration() != null) {
            timing.add(getString(R.string.duration) + drug.getDuration());
        }
        return timing;
    }

    private String capitalise(String string) {
        return string == null || string.length() < 2 ? string :
                Character.toUpperCase(string.charAt(0)) + string.substring(1);
    }

}
