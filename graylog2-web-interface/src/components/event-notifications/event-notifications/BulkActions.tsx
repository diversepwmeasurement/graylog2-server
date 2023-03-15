/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
import * as React from 'react';
import uniq from 'lodash/uniq';
import { useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import ApiRoutes from 'routing/ApiRoutes';
import type FetchError from 'logic/errors/FetchError';
import fetch from 'logic/rest/FetchProvider';
import { qualifyUrl } from 'util/URLUtils';
import UserNotification from 'util/UserNotification';
import { Button } from 'components/bootstrap';
import StringUtils from 'util/StringUtils';

type Props = {
  selectedNotificationsIds: Array<string>,
  setSelectedNotificationsIds: (definitionIds: Array<string>) => void,
  refetchEventNotifications: () => void,
};

const BulkActions = ({ selectedNotificationsIds, setSelectedNotificationsIds, refetchEventNotifications }: Props) => {
  const queryClient = useQueryClient();
  const selectedItemsAmount = selectedNotificationsIds?.length;
  const descriptor = StringUtils.pluralize(selectedItemsAmount, 'event notification', 'event notifications');
  const onDelete = useCallback(() => {
    // eslint-disable-next-line no-alert
    if (window.confirm(`Do you really want to remove ${selectedItemsAmount} ${descriptor}?`)) {
      const deleteCalls = selectedNotificationsIds.map((notificationId) => fetch('DELETE', qualifyUrl(ApiRoutes.EventNotificationsApiController.delete(notificationId).url)).then(() => notificationId));

      Promise.allSettled(deleteCalls).then((result) => {
        const fulfilledRequests = result.filter((response) => response.status === 'fulfilled') as Array<{ status: 'fulfilled', value: string }>;
        const deletedNotificationIds = fulfilledRequests.map(({ value }) => value);
        const notDeletednotificationIds = selectedNotificationsIds?.filter((streamId) => !deletedNotificationIds.includes(streamId));

        if (notDeletednotificationIds.length) {
          const rejectedRequests = result.filter((response) => response.status === 'rejected') as Array<{ status: 'rejected', reason: FetchError }>;
          const errorMessages = uniq(rejectedRequests.map((request) => request.reason.responseMessage));

          if (notDeletednotificationIds.length !== selectedNotificationsIds.length) {
            queryClient.invalidateQueries(['eventNotifications', 'overview']);
          }

          UserNotification.error(`${notDeletednotificationIds.length} out of ${selectedNotificationsIds} selected ${descriptor} could not be deleted. Status: ${errorMessages.join()}`);

          return;
        }

        queryClient.invalidateQueries(['eventNotifications', 'overview']);
        setSelectedNotificationsIds(notDeletednotificationIds);
        refetchEventNotifications();
        UserNotification.success(`${selectedItemsAmount} ${descriptor} ${StringUtils.pluralize(selectedItemsAmount, 'was', 'were')} deleted successfully.`, 'Success');
      });
    }
  }, [descriptor, queryClient, refetchEventNotifications, selectedNotificationsIds, selectedItemsAmount, setSelectedNotificationsIds]);

  return (
    <Button bsSize="xsmall" bsStyle="danger" onClick={() => onDelete()}>Delete</Button>
  );
};

export default BulkActions;
