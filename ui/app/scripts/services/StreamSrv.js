(function() {
    'use strict';
    angular.module('theHiveServices').factory('StreamSrv', function($http, $timeout, UserSrv, AuthenticationSrv, AfkSrv, AlertSrv) {
        var callbacks = { /* id: { objectType: [ cb ] } */ };

        var self = {
            isPolling: false,
            streamId: null,

            init: function() {
                self.streamId = null;
                self.requestStream();
            },

            runCallbacks: function(id, objectType, message) {
                var cbs = callbacks[id];
                if (angular.isDefined(cbs)) {
                    angular.forEach(cbs[objectType], function(cb) {
                        cb(message);
                    });
                }
            },

            handleStreamResponse: function(data) {
                console.log(data);
                var byRootIds = {};
                var byObjectTypes = {};
                var byRootIdsWithObjectTypes = {};
                angular.forEach(data, function(message) {
                    var rootId = message.base.rootId;
                    var objectType = message.base.objectType;
                    var rootIdWithObjectType = rootId + '|' + objectType;

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
                });

                angular.forEach(byRootIds, function(messages, rootId) {
                    self.runCallbacks(rootId, 'any', messages);
                });
                angular.forEach(byObjectTypes, function(messages, objectType) {
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
                $http.get('/api/stream/' + self.streamId).success(function(data, status) {
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
                                    AlertSrv.error('StreamSrv', response.data, response.status);
                                });
                        });
                    }
                    self.poll();

                }).error(function(data, status) {
                    // Initialize the stream;
                    self.isPolling = false;

                    if (status !== 404) {
                        AlertSrv.error('StreamSrv', data, status);

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

                $http.post('/api/stream').success(function(streamId) {
                    self.streamId = streamId;
                    self.poll(self.streamId);
                }).error(function(data, status) {
                    AlertSrv.error('StreamSrv', data, status);
                });
            },

            listen: function(rootId, objectType, cb) {
                if (angular.isDefined(callbacks[rootId])) {
                    if (angular.isDefined(callbacks[rootId][objectType])) {
                        callbacks[rootId][objectType].push(cb);
                    } else {
                        callbacks[rootId][objectType] = [cb];
                    }
                } else {
                    callbacks[rootId] = {};
                    callbacks[rootId][objectType] = [cb];
                }
            }
        };

        return self;
    });
})();
