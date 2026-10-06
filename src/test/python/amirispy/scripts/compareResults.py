#!/usr/bin/env python

# SPDX-FileCopyrightText: 2022 German Aerospace Center <amiris@dlr.de>
#
# SPDX-License-Identifier: Apache-2.0

import sys
from typing import List

from amirispy.source.file_comparison import match_files, compare_files
from amirispy.source.files import get_all_csv_files_in_folder_except, get_file_name_stem
from amirispy.source.logs import set_up_logger, LogLevels


def compare_results(
    folder_expected: str, folder_to_test: str, ignore_list: List[str] = None
) -> None:
    """
    Compares content of two folders with AMIRIS results in CSV format for equivalence
    Args:
        folder_expected: folder with expected results
        folder_to_test: folder with results to test against expected results
        ignore_list: optional list of file names to ignore
    """
    set_up_logger(LogLevels.WARN)

    expected_files = get_all_csv_files_in_folder_except(folder_expected, ignore_list)
    test_files = get_all_csv_files_in_folder_except(folder_to_test, ignore_list)
    file_pairs = match_files(expected_files, test_files)

    print(f"Checking {len(expected_files)} expected files...")
    results = {}
    for expected, to_test in file_pairs.items():
        if to_test:
            results[expected] = compare_files(expected, to_test)
        else:
            results[expected] = (
                f"Missing file in test folder: {get_file_name_stem(expected)}"
            )

    differences_found = False
    for file, differences in results.items():
        if differences:
            differences_found = True
            print(
                f"FAIL: Found differences for {get_file_name_stem(file)}: {differences}"
            )

    if not differences_found:
        print("PASS: Found no significant differences for any expected pair of files.")


if __name__ == "__main__":
    args = sys.argv
    exceptions = args[3] if len(args) >= 4 else None
    compare_results(args[1], args[2], exceptions)
