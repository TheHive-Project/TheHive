(function() {
    'use strict';
    angular.module('theHiveServices').factory('StreamSrv', function($rootScope, $http, $timeout, UserSrv, AuthenticationSrv, AfkSrv, NotificationSrv) {

        var self = {
            isPolling: false,
            streamId: null,

            init: function() {
                self.streamId = null;
                self.requestStream();
            },

            runCallbacks: function(id, objectType, message) {
                $rootScope.$broadcast('stream:' + id + '-' + objectType, message);
            },

            handleStreamResponse: function(data) {
                if(!data || data.length === 0) {
                    return;
                }

                var byRootIds = {};
                var byObjectTypes = {};
                var byRootIdsWithObjectTypes = {};
                var bySecondaryObjectTypes = {};

                angular.forEach(data, function(message) {
                    var rootId = message.base.rootId;
                    var objectType = message.base.objectType;
                    var rootIdWithObjectType = rootId + '|' + objectType;
                    var secondaryObjectTypes = message.summary ? _.without(_.keys(message.summary), objectType) : [];

                    if (rootId in byRootIds) {
                        byRootIds[rootId].push(message);
                    } else {
                        byRootIds[rootId] = [message];
                    }

                    if (objectType in byObjectTypes) {
                        byObjectTypes[objectType].push(message);
                    } else {
                        byObjectTypes[objectType] = [message];
                    }

                    if (rootIdWithObjectType in byRootIdsWithObjectTypes) {
                        byRootIdsWithObjectTypes[rootIdWithObjectType].push(message);
                    } else {
                        byRootIdsWithObjectTypes[rootIdWithObjectType] = [message];
                    }

                    _.each(secondaryObjectTypes, function(type) {
                        if (type in bySecondaryObjectTypes) {
                            bySecondaryObjectTypes[type].push(message);
                        } else {
                            bySecondaryObjectTypes[type] = [message];
                        }
                    });

                });

                angular.forEach(byRootIds, function(messages, rootId) {
                    self.runCallbacks(rootId, 'any', messages);
                });
                angular.forEach(byObjectTypes, function(messages, objectType) {
                    self.runCallbacks('any', objectType, messages);
                });

                // Trigger strem event for sub object types
                angular.forEach(bySecondaryObjectTypes, function(messages, objectType) {
                    self.runCallbacks('any', objectType, messages);
                });

                angular.forEach(byRootIdsWithObjectTypes, function(messages, rootIdWithObjectType) {
                    var temp = rootIdWithObjectType.split('|', 2),
                        rootId = temp[0],
                        objectType = temp[1];

                    self.runCallbacks(rootId, objectType, messages);
                });

                self.runCallbacks('any', 'any', data);
            },

            poll: function() {
                // Skip polling is a poll is already running
                if (self.streamId === null || self.isPolling === true) {
                    return;
                }

                // Flag polling start
                self.isPolling = true;

                // Poll stream changes
                $http.get('./api/stream/' + self.streamId).success(function(data, status) {
                    // Flag polling end
                    self.isPolling = false;

                    // Handle stream data and callbacks
                    self.handleStreamResponse(data);

                    // Check if the session will expire soon
                    if (status === 220) {
                        AfkSrv.prompt().then(function() {
                            UserSrv.getUserInfo(AuthenticationSrv.currentUser.id)
                                .then(function() {

                                }, function(response) {
                                    NotificationSrv.error('StreamSrv', response.data, response.status);
                                });
                        });
                    }
                    self.poll();

                }).error(function(data, status) {
                    // Initialize the stream;
                    self.isPolling = false;

                    if (status !== 404) {
                        NotificationSrv.error('StreamSrv', data, status);

                        if (status === 401) {
                            return;
                        }
                    }

                    self.init();
                });
            },


            requestStream: function() {
                if (self.streamId !== null) {
                    return;
                }

                $http.post('./api/stream').success(function(streamId) {
                    self.streamId = streamId;
                    self.poll(self.streamId);
                }).error(function(data, status) {
                    NotificationSrv.error('StreamSrv', data, status);
                });
            },

            /**
             * @param config {Object} This configuration object has the following attributes
             * <li>rootId</li>
             * <li>objectType {String}</li>
             * <li>scope {Object}</li>
             * <li>callback {Function}</li>
             */
            addListener: function(config) {
                if(!config.scope) {
                    console.error('No scope provided, use the old listen method', config);
                    self.listen(config.rootId, config.objectType, config.callback);
                    return;
                }

                var eventName = 'stream:' + config.rootId + '-' + config.objectType;
                config.scope.$on(eventName, function(event, data) {
                    config.callback(data);
                });

            }
        };

        return self;
    });
})();
