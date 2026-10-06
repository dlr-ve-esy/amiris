# SPDX-FileCopyrightText: 2022 German Aerospace Center <amiris@dlr.de>
#
# SPDX-License-Identifier: Apache-2.0

import logging
from typing import List, Dict, Tuple

import numpy as np
import pandas as pd
from numpy import ndarray

from amirispy.source.files import get_file_name_stem

MISSING_EXPECTED_FILE = "Expected file {} was not found among files in the test folder."
TEST_FILE_UNMATCHED = "Missing file to compare with file {} found in test folder."
VALUE_DISALLOWED = "Found non-numeric and non-string value."
ALL_VALUES_EQUIVALENT = "Content of files is equivalent for {}."
VALUES_NOT_EQUIVALENT = "Files are not equivalent for {}."
SHAPE_MISMATCH = "File to test {} has different shape than expected: {}"


def match_files(expected_files: List[str], test_files: List[str]) -> Dict[str, str]:
    """
    Args:
        expected_files: paths of expected files
        test_files: path of files for text
    Returns: Match of each expected file to its test file, or to an empty string if file is not found
    """
    expected_pairs = {get_file_name_stem(item).upper(): item for item in expected_files}
    test_pairs = {get_file_name_stem(item).upper(): item for item in test_files}
    result = {}
    for expected_stem, expected_file in expected_pairs.items():
        if expected_stem in test_pairs.keys():
            result[expected_file] = test_pairs.pop(expected_stem)
        else:
            result[expected_file] = ""
            logging.error(MISSING_EXPECTED_FILE.format(expected_stem))
    if len(test_pairs) > 0:
        for test_stem in test_pairs.keys():
            logging.warning(TEST_FILE_UNMATCHED.format(test_stem))
    return result


def compare_files(expected: str, to_test: str) -> str:
    """
    Compares two csv files for equivalence
    Args:
        expected: file with expected data
        to_test: file with data to test
    Raises:
        Exception: if given files are not equivalent
    Returns: Empty string if everything matched, else details on the differences
    """
    expected_df = read_file_and_sort_df(expected)
    test_df = read_file_and_sort_df(to_test)
    result = ""
    if expected_df.shape != test_df.shape:
        logging.error(
            SHAPE_MISMATCH.format(get_file_name_stem(to_test), expected_df.shape)
        )
        result = analyse_shape_difference(expected_df.shape, test_df.shape)
    else:
        all_values_compared = np.isclose(test_df, expected_df)
        if np.all(all_values_compared):
            logging.info(ALL_VALUES_EQUIVALENT.format(get_file_name_stem(to_test)))
        else:
            logging.error(VALUES_NOT_EQUIVALENT.format(get_file_name_stem(to_test)))
            result = analyse_row_difference(test_df, all_values_compared)
    return result


def analyse_shape_difference(
    expected_shape: Tuple[int, int], test_shape: Tuple[int, int]
) -> str:
    """
    Analyses difference between two two-dimensional dataframe shapes
    Args:
        expected_shape: reference shape
        test_shape: shape to check against the reference
    Returns: description of the shape difference
    """
    row_delta = test_shape[0] - expected_shape[0]
    column_delta = test_shape[1] - expected_shape[1]
    result = ""
    if row_delta != 0:
        result += f"Test file {'has extra' if row_delta > 0 else 'misses' } {abs(row_delta)} row(s). "
    if column_delta != 0:
        result += f"Test file {'has extra' if row_delta > 0 else 'misses'} {abs(column_delta)} column(s). "
    return result


def analyse_row_difference(test_data: pd.DataFrame, comparison_result: ndarray) -> str:
    """
    Returns list of rows in the given test data frame that contain at least on mismatch in the comparison
    Args:
        test_data: original test data set with proper index
        comparison_result: array containing True or False for each row and column, where False indicates a mismatch to
                           the reference data set
    Returns: String listing all row numbers of the test data set that did not match the reference data
    """
    compare_df = pd.DataFrame(comparison_result, index=test_data.index)
    filtered = compare_df[(compare_df == False).any(axis=1)]  # noqa
    adjust_line_count_for_header_and_start_1 = [i + 2 for i in filtered.index.to_list()]
    return (
        f"Deviations in test file line(s): {adjust_line_count_for_header_and_start_1}"
    )


def read_file_and_sort_df(file_name: str) -> pd.DataFrame:
    """
    Reads given file to data frame, replaces missing values with 0, converts non-numeric values to digits
    and sorts from left to right columns
    Args:
        file_name: file to read
    Returns: sorted data frame of file content with no NaNs
    """
    df = pd.read_csv(file_name, sep=";")
    df.fillna(0, inplace=True)
    df = df.apply(convert_string_to_digit)
    return df.sort_values(by=list(df.columns.values))


def convert_string_to_digit(values: pd.Series) -> pd.Series:
    """
    Takes Series of any numeric or string values. Converts strings to a numeric value; does not change numeric input
    Args:
        values: float or string value
    Returns: Series of numeric representation also for string input, yields same value for same input
    """
    return values.apply(
        lambda x: sum([ord(char) for char in x]) if isinstance(x, str) else x
    )
