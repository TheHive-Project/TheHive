(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('AuditSrv', function($http, StreamSrv, NotificationSrv) {
            return function(rootId, max, scope) {
                var ret = [];
                if (!isFinite(max)) {
                    max = 10;
                }

                $http.get('./api/flow', {
                    'params': {
                        'rootId': rootId,
                        'count': max
                    }
                }).then(function(response) {
                    var data = response.data;

                    angular.forEach(data, function(d) {
                        ret.push(d);
                    });
                    var fnSameRequestId = function(message) {
                        return function(m) { 
                            return m.base.requestId === message.base.requestId;
                        };
                    };

                    var eventConfig = {
                        rootId: rootId,
                        objectType: 'any',
                        scope: scope,
                        callback: function(messages) {
                            for (var i = messages.length - 1, messageAdded = 0; i >= 0 && messageAdded < max; i--) {
                                var message = messages[i];
                                var alreadyInFlow = _.find(ret, fnSameRequestId(message)) !== undefined;
                                if (!alreadyInFlow && message.base.objectType !== 'user') {
                                    var index = messageAdded;
                                    ret.splice(index, 0, message);
                                    if (ret.length > max) {
                                        ret.pop();
                                    }
                                    messageAdded += 1;
                                } else if(alreadyInFlow && message.base.objectType === 'case_artifact_job') {
                                    ret[messageAdded] = message;
                                } else if(alreadyInFlow && message.base.objectType === 'action') {
                                    ret[messageAdded] = message;
                                }

                            }
                        }
                    };

                    StreamSrv.addListener(eventConfig);
                }).catch(function(data, status) {
                    NotificationSrv.error('AuditSrv', data, status);
                });
                return ret;
            };
        });
})();
