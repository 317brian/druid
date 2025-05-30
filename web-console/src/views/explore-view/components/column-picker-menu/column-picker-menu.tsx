/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import type { IconName } from '@blueprintjs/core';
import { ContextMenu, Icon, InputGroup, Menu, MenuItem } from '@blueprintjs/core';
import { IconNames } from '@blueprintjs/icons';
import classNames from 'classnames';
import type { Column } from 'druid-query-toolkit';
import { C } from 'druid-query-toolkit';
import { useState } from 'react';

import { caseInsensitiveContains, columnToIcon, copyAndAlert, filterMap } from '../../../../utils';

import './column-picker-menu.scss';

export interface ColumnPickerMenuProps {
  className?: string;
  columns: readonly Column[];
  onSelectColumn(column: Column): void;
  rightIconForColumn?: (column: Column) => IconName | undefined;
  onSelectNone?: () => void;
  shouldDismissPopover?: boolean;
}

export const ColumnPickerMenu = function ColumnPickerMenu(props: ColumnPickerMenuProps) {
  const {
    className,
    columns,
    onSelectColumn,
    onSelectNone,
    rightIconForColumn,
    shouldDismissPopover,
  } = props;
  const [columnSearch, setColumnSearch] = useState('');

  return (
    <div className={classNames('column-picker-menu', className)}>
      <InputGroup
        className="search-input"
        value={columnSearch}
        onChange={e => setColumnSearch(e.target.value)}
        placeholder="Search"
        autoFocus
      />
      <Menu className="column-menu">
        {onSelectNone && (
          <MenuItem
            icon={IconNames.BLANK}
            text="None"
            onClick={onSelectNone}
            shouldDismissPopover={shouldDismissPopover}
          />
        )}
        {filterMap(columns, (c, i) => {
          const columnName = c.name;
          if (!caseInsensitiveContains(columnName, columnSearch)) return;
          const iconName = rightIconForColumn?.(c);
          return (
            <ContextMenu
              key={i}
              content={
                <Menu>
                  <MenuItem
                    text="Copy"
                    onClick={() => copyAndAlert(String(columnName), `Copied to clipboard`)}
                  />
                  <MenuItem
                    text="Copy as SQL column"
                    onClick={() => copyAndAlert(String(C(columnName)), `Copied to clipboard`)}
                  />
                </Menu>
              }
            >
              <MenuItem
                icon={columnToIcon(c) || IconNames.BLANK}
                text={columnName}
                labelElement={iconName && <Icon icon={iconName} />}
                onClick={() => onSelectColumn(c)}
                shouldDismissPopover={shouldDismissPopover}
              />
            </ContextMenu>
          );
        })}
      </Menu>
    </div>
  );
};
