(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('AuditSrv', function($http, StreamSrv, AlertSrv) {
            return function(rootId, max) {
                var ret = [];
                if (!isFinite(max)) {
                    max = 10;
                }

                $http.get('/api/flow', {
                    'params': {
                        'rootId': rootId,
                        'count': max
                    }
                }).success(function(data) {
                    angular.forEach(data, function(d) {
                        ret.push(d);
                    });
                    var fnSameRequestId = function(message) {
                        return function(m) { 
                            return m.base.requestId === message.base.requestId;
                        };
                    };
                    StreamSrv.listen(rootId, 'any', function(messages) {
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
                            }

                        }
                    });
                }).error(function(data, status) {
                    AlertSrv.error('AuditSrv', data, status);
                });
                return ret;
            };
        });
})();
